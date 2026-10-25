package dev.localvispr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import dev.localvispr.AppContainer
import dev.localvispr.MainActivity
import dev.localvispr.R
import dev.localvispr.accessibility.VisprAccessibilityService
import dev.localvispr.audio.VoiceRecorder
import dev.localvispr.text.TranscriptPostProcessor
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DictationState {
    IDLE,
    LISTENING,
    PROCESSING,
}

// ==============================================================================
// Foreground overlay bubble service (`TYPE_APPLICATION_OVERLAY`)
// ==============================================================================
//
// Shows a draggable floating pill on top of other apps. Tapping once starts
// 16 kHz microphone capture; tapping again runs offline Whisper + the local
// cleanup pipeline and injects the result through `VisprAccessibilityService`.

class FloatingBubbleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val recorder = VoiceRecorder()
    private var windowManager: WindowManager? = null
    private var bubbleView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (Settings.canDrawOverlays(this)) {
            attachBubble()
        }
        _running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        _running.value = false
        _dictationState.value = DictationState.IDLE
        if (recorder.isRecording) recorder.stopAndTakeSamples()
        bubbleView?.let { runCatching { windowManager?.removeView(it) } }
        bubbleView = null
        scope.cancel()
        super.onDestroy()
    }

    private fun onBubbleTapped() {
        when (_dictationState.value) {
            DictationState.IDLE -> startDictation()
            DictationState.LISTENING -> finishDictation()
            DictationState.PROCESSING -> Unit
        }
    }

    private fun startDictation() {
        val container = AppContainer.get(this)
        val sttSpec = container.settings.state.value.sttModel
        if (!container.models.isInstalled(sttSpec)) {
            Toast.makeText(this, R.string.toast_model_missing, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        if (recorder.start()) {
            setState(DictationState.LISTENING)
        } else {
            Toast.makeText(this, R.string.toast_mic_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishDictation() {
        setState(DictationState.PROCESSING)
        val container = AppContainer.get(this)
        val settings = container.settings.state.value
        val surrounding = VisprAccessibilityService.instance.value?.readTextBeforeCursor()

        scope.launch {
            val cleaned =
                withContext(Dispatchers.Default) {
                    val samples = recorder.stopAndTakeSamples()
                    val transcription =
                        container.transcriber.transcribe(
                            samples = samples,
                            spec = settings.sttModel,
                            fixedLanguage = settings.languagePreference.fixedLanguage,
                        )
                    if (transcription.rawText.isBlank()) {
                        ""
                    } else {
                        val llmClient = container.llmEngine.clientFor(settings.llmModel)
                        TranscriptPostProcessor(llmClient)
                            .process(
                                rawTranscript = transcription.rawText,
                                language = transcription.detectedLanguage,
                                surroundingText = surrounding,
                                dictionaryWords = settings.dictionaryWords,
                            )
                    }
                }

            setState(DictationState.IDLE)
            if (cleaned.isNotEmpty()) {
                _lastResult.value = cleaned
                val service = VisprAccessibilityService.instance.value
                if (service != null) {
                    service.insertText(cleaned)
                } else {
                    Toast.makeText(this@FloatingBubbleService, cleaned, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setState(next: DictationState) {
        _dictationState.value = next
        bubbleView?.let(::styleBubble)
    }

    private fun attachBubble() {
        val wm = getSystemService(WindowManager::class.java) ?: return
        windowManager = wm

        val params =
            WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                )
                .apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = dp(16)
                    y = dp(180)
                }

        val view =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(18), dp(12), dp(18), dp(12))
                elevation = dp(6).toFloat()
            }
        styleBubble(view)
        installDragAndTapListener(view, params, wm)
        wm.addView(view, params)
        bubbleView = view
    }

    private fun styleBubble(view: TextView) {
        val (labelRes, bgColor) =
            when (_dictationState.value) {
                DictationState.IDLE -> R.string.bubble_idle to Color.parseColor("#1E293B")
                DictationState.LISTENING -> R.string.bubble_listening to Color.parseColor("#DC2626")
                DictationState.PROCESSING ->
                    R.string.bubble_processing to Color.parseColor("#D97706")
            }
        view.setText(labelRes)
        view.background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(bgColor)
            }
    }

    private fun installDragAndTapListener(
        view: View,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false
        val slop = dp(8)

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > slop || abs(dy) > slop) dragged = true
                    if (dragged) {
                        params.x = startX + dx
                        params.y = startY + dy
                        wm.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) {
                        v.performClick()
                        onBubbleTapped()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val openAppIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                Intent(this, FloatingBubbleService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_body))
                .setContentIntent(openAppIntent)
                .addAction(
                    Notification.Action.Builder(
                            null,
                            getString(R.string.notification_stop),
                            stopIntent,
                        )
                        .build()
                )
                .setOngoing(true)
                .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                resources.displayMetrics,
            )
            .toInt()

    companion object {
        private const val CHANNEL_ID = "localvispr_bubble"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "dev.localvispr.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _dictationState = MutableStateFlow(DictationState.IDLE)
        val dictationState: StateFlow<DictationState> = _dictationState.asStateFlow()

        private val _lastResult = MutableStateFlow("")
        val lastResult: StateFlow<String> = _lastResult.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, FloatingBubbleService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
    }
}
