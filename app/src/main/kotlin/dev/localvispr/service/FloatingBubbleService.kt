package dev.localvispr.service

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.Toast
import dev.localvispr.AppContainer
import dev.localvispr.MainActivity
import dev.localvispr.R
import dev.localvispr.accessibility.VisprAccessibilityService
import dev.localvispr.audio.VoiceRecorder
import dev.localvispr.sensors.ShakeDetector
import dev.localvispr.settings.UserSettings
import dev.localvispr.text.EditorContext
import dev.localvispr.text.FillerWordFilter
import dev.localvispr.text.LiveStreamingDiff
import dev.localvispr.text.TranscriptPostProcessor
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
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
// Shows a draggable circular Cyberpunk-V bubble on top of other apps. When the
// user starts dictating, a companion equalizer pill (`WaveformPillView`) pops up
// next to it — just like Wispr Flow's `BubblePOPOComposable` — showing 10 bars
// that stay still on silence and move only when speech arrives.

class FloatingBubbleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val recorder = VoiceRecorder()
    private val container by lazy { AppContainer.get(this) }
    private val haptics by lazy { BubbleHaptics(this) }
    private var shakeDetector: ShakeDetector? = null

    private var windowManager: WindowManager? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var rootContainer: LinearLayout? = null
    private var bubbleView: CyberpunkVBubbleView? = null
    private var waveformPillView: WaveformPillView? = null
    private var snapAnimator: ValueAnimator? = null
    private var dockedRight: Boolean = false
    private var lastContainerWidth: Int = 0
    private var targetVisible: Boolean? = null

    private var liveStreamingJob: Job? = null
    private var streamedSoFar: String = ""
    private var initialEditorContext: EditorContext? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (Settings.canDrawOverlays(this)) {
            attachBubble()
        }
        // Forward live microphone speech levels into the equalizer pill.
        scope.launch {
            recorder.audioLevel.collect { level -> waveformPillView?.setAudioLevel(level) }
        }

        shakeDetector = ShakeDetector(this) { onBubbleTapped() }
        scope.launch {
            container.settings.state.collect { settings ->
                if (settings.shakeToDictateEnabled) {
                    shakeDetector?.start()
                } else {
                    shakeDetector?.stop()
                }
            }
        }

        scope.launch {
            combine(
                    VisprAccessibilityService.hasEditableFocus,
                    _dictationState,
                    container.settings.state,
                ) { _, _, _ ->
                    Unit
                }
                .collect { updateBubbleVisibility() }
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
        liveStreamingJob?.cancel()
        liveStreamingJob = null
        if (streamedSoFar.isNotEmpty()) {
            VisprAccessibilityService.instance.value?.applyStreamingDelta(streamedSoFar.length, "")
            streamedSoFar = ""
        }
        initialEditorContext = null
        if (recorder.isRecording) recorder.cancel()
        shakeDetector?.stop()
        shakeDetector = null
        snapAnimator?.cancel()
        snapAnimator = null
        rootContainer?.animate()?.cancel()
        rootContainer?.let { runCatching { windowManager?.removeView(it) } }
        rootContainer = null
        overlayParams = null
        bubbleView = null
        waveformPillView = null
        scope.cancel()
        super.onDestroy()
    }

    // Animates the bubble in and out with scale + alpha + edge slide (modelled
    // after Wispr Flow's `BubbleComposableKt` `bubbleAlpha` / `bubbleScale`)
    // instead of popping via a raw `visibility` flip.
    private fun updateBubbleVisibility() {
        val view = rootContainer ?: return
        val settings = container.settings.state.value
        val show =
            BubbleVisibilityPolicy.shouldShowBubble(
                hasEditableFocus = VisprAccessibilityService.hasEditableFocus.value,
                isDictatingOrProcessing = _dictationState.value != DictationState.IDLE,
                snoozedUntilEpochMs = settings.snoozedUntilEpochMs,
                nowEpochMs = System.currentTimeMillis(),
            )
        if (targetVisible == show) return
        targetVisible = show

        val edgeSlidePx = (if (dockedRight) dp(24) else -dp(24)).toFloat()
        view.animate().cancel()

        if (show) {
            if (view.visibility != View.VISIBLE) {
                view.alpha = 0f
                view.scaleX = 0.25f
                view.scaleY = 0.25f
                view.translationX = edgeSlidePx
                view.visibility = View.VISIBLE
            }
            view.pivotX =
                if (dockedRight) view.width.toFloat().coerceAtLeast(dp(64).toFloat()) else 0f
            view.pivotY = view.height.toFloat().coerceAtLeast(dp(64).toFloat()) / 2f
            view
                .animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .setDuration(230L)
                .setInterpolator(OvershootInterpolator(1.35f))
                .withEndAction(null)
                .start()
        } else {
            view.pivotX =
                if (dockedRight) view.width.toFloat().coerceAtLeast(dp(64).toFloat()) else 0f
            view.pivotY = view.height.toFloat().coerceAtLeast(dp(64).toFloat()) / 2f
            view
                .animate()
                .alpha(0f)
                .scaleX(0.25f)
                .scaleY(0.25f)
                .translationX(edgeSlidePx)
                .setDuration(160L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    if (targetVisible == false) {
                        view.visibility = View.GONE
                    }
                }
                .start()
        }
    }

    private fun onBubbleTapped() {
        when (_dictationState.value) {
            DictationState.IDLE -> startDictation()
            DictationState.LISTENING -> finishDictation()
            DictationState.PROCESSING -> Unit
        }
    }

    private fun startDictation() {
        val settings = container.settings.state.value
        val sttSpec = settings.sttModel
        if (!container.models.isInstalled(sttSpec)) {
            Toast.makeText(this, R.string.toast_model_missing, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        streamedSoFar = ""
        initialEditorContext = VisprAccessibilityService.instance.value?.readEditorContext()
        if (recorder.start()) {
            setState(DictationState.LISTENING)
            if (settings.hapticsEnabled) {
                haptics.onStartRecording()
            }
            if (settings.liveStreamingEnabled) {
                startLiveStreaming(settings)
            }
        } else {
            Toast.makeText(this, R.string.toast_mic_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLiveStreaming(settings: UserSettings) {
        liveStreamingJob?.cancel()
        liveStreamingJob =
            scope.launch {
                val hotwords = TranscriptPostProcessor.formatHotwords(settings.dictionaryWords)
                val initialBefore = initialEditorContext?.textBeforeCursor
                while (isActive && _dictationState.value == DictationState.LISTENING) {
                    delay(1200L)
                    if (!isActive || _dictationState.value != DictationState.LISTENING) break

                    val snapshot = recorder.snapshotSamples()
                    if (snapshot.isEmpty()) continue

                    val candidate =
                        withContext(Dispatchers.Default) {
                            val transcription =
                                container.transcriber.transcribe(
                                    samples = snapshot,
                                    spec = settings.sttModel,
                                    fixedLanguage = settings.languagePreference.fixedLanguage,
                                    hotwords = hotwords,
                                )
                            if (transcription.rawText.isBlank()) {
                                ""
                            } else {
                                FillerWordFilter.clean(
                                    transcription.rawText,
                                    transcription.detectedLanguage,
                                )
                            }
                        }

                    if (!isActive || _dictationState.value != DictationState.LISTENING) break

                    if (candidate.isNotEmpty()) {
                        val formattedCandidate =
                            TranscriptPostProcessor.formatForInsertion(candidate, initialBefore)
                        val delta =
                            LiveStreamingDiff.computeDelta(streamedSoFar, formattedCandidate)
                        if (delta.deleteBeforeCursor > 0 || delta.textToCommit.isNotEmpty()) {
                            val service = VisprAccessibilityService.instance.value
                            if (
                                service?.applyStreamingDelta(
                                    delta.deleteBeforeCursor,
                                    delta.textToCommit,
                                ) == true
                            ) {
                                streamedSoFar = formattedCandidate
                            }
                        }
                    }
                }
            }
    }

    private fun cancelDictation() {
        liveStreamingJob?.cancel()
        liveStreamingJob = null
        if (streamedSoFar.isNotEmpty()) {
            VisprAccessibilityService.instance.value?.applyStreamingDelta(streamedSoFar.length, "")
            streamedSoFar = ""
        }
        initialEditorContext = null
        recorder.cancel()
        val settings = container.settings.state.value
        if (settings.hapticsEnabled) {
            haptics.onCancelled()
        }
        setState(DictationState.IDLE)
    }

    private fun finishDictation() {
        if (_dictationState.value != DictationState.LISTENING) return
        liveStreamingJob?.cancel()
        liveStreamingJob = null
        setState(DictationState.PROCESSING)
        val settings = container.settings.state.value
        if (settings.hapticsEnabled) {
            haptics.onStopRecording()
        }
        val editorContext =
            initialEditorContext ?: VisprAccessibilityService.instance.value?.readEditorContext()
        val surrounding = editorContext?.textBeforeCursor
        val targetApp = editorContext?.packageName

        scope.launch {
            val samples = recorder.stopAndTakeSamples()
            val hotwords = TranscriptPostProcessor.formatHotwords(settings.dictionaryWords)
            val transcription =
                withContext(Dispatchers.Default) {
                    container.transcriber.transcribe(
                        samples = samples,
                        spec = settings.sttModel,
                        fixedLanguage = settings.languagePreference.fixedLanguage,
                        hotwords = hotwords,
                    )
                }

            val cleaned =
                if (transcription.rawText.isBlank()) {
                    ""
                } else {
                    withContext(Dispatchers.Default) {
                        val llmClient = container.llmEngine.clientFor(settings.llmModel)
                        TranscriptPostProcessor(llmClient)
                            .process(
                                rawTranscript = transcription.rawText,
                                language = transcription.detectedLanguage,
                                surroundingText = surrounding,
                                dictionaryWords = settings.dictionaryWords,
                                style = settings.dictationStyle,
                                targetAppPackage = targetApp,
                            )
                    }
                }

            setState(DictationState.IDLE)

            val service = VisprAccessibilityService.instance.value
            val finalFormatted =
                if (cleaned.isNotEmpty()) {
                    TranscriptPostProcessor.formatForInsertion(cleaned, surrounding)
                } else {
                    ""
                }

            if (streamedSoFar.isNotEmpty()) {
                val delta = LiveStreamingDiff.computeDelta(streamedSoFar, finalFormatted)
                if (service != null) {
                    service.applyStreamingDelta(delta.deleteBeforeCursor, delta.textToCommit)
                } else if (cleaned.isNotEmpty()) {
                    Toast.makeText(this@FloatingBubbleService, cleaned, Toast.LENGTH_LONG).show()
                }
            } else if (cleaned.isNotEmpty()) {
                if (service != null) {
                    service.insertText(cleaned)
                } else {
                    Toast.makeText(this@FloatingBubbleService, cleaned, Toast.LENGTH_LONG).show()
                }
            }

            if (cleaned.isNotEmpty()) {
                _lastResult.value = cleaned
                container.history.record(cleaned, transcription.detectedLanguage.code)
                if (settings.hapticsEnabled) {
                    haptics.onInserted()
                }
            }

            streamedSoFar = ""
            initialEditorContext = null
        }
    }

    private fun setState(next: DictationState) {
        _dictationState.value = next
        bubbleView?.setDictationState(next)
        waveformPillView?.setDictationState(next)
        updateBubbleVisibility()
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
                    // Use absolute LEFT/TOP coordinates like Wispr Flow's `createOverlayParams`.
                    gravity = Gravity.TOP or Gravity.LEFT
                    windowAnimations = 0
                    suppressMoveAnimation(this)
                    x = dp(12)
                    y = dp(220)
                }
        overlayParams = params

        val vBubble =
            CyberpunkVBubbleView(this).apply {
                elevation = dp(8).toFloat()
                setDictationState(_dictationState.value)
            }
        val pill =
            WaveformPillView(
                    context = this,
                    onCancelTapped = ::cancelDictation,
                    onConfirmTapped = ::finishDictation,
                )
                .apply {
                    elevation = dp(8).toFloat()
                    setDictationState(_dictationState.value)
                }

        val container =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
                visibility = View.GONE
                alpha = 0f
                scaleX = 0.25f
                scaleY = 0.25f
            }

        rootContainer = container
        bubbleView = vBubble
        waveformPillView = pill
        rebuildDockOrder(dockRight = false)

        // Mirrors Wispr Flow's `FlowScreenLayoutManager.onLayoutChange`: when docked on
        // the right edge and the companion equalizer pill expands/collapses, shift `x`
        // by `-deltaWidth` so the V bubble stays anchored at the right screen edge.
        container.addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
            val newWidth = right - left
            if (newWidth > 0 && newWidth != lastContainerWidth) {
                val prevWidth = lastContainerWidth
                lastContainerWidth = newWidth
                if (prevWidth > 0 && dockedRight) {
                    params.x = (params.x - (newWidth - prevWidth)).coerceAtLeast(dp(12))
                    runCatching { wm.updateViewLayout(view, params) }
                }
            }
        }

        installDragAndTapListener(vBubble, container, params, wm)
        wm.addView(container, params)
        updateBubbleVisibility()
    }

    private fun rebuildDockOrder(dockRight: Boolean) {
        val container = rootContainer ?: return
        val vBubble = bubbleView ?: return
        val pill = waveformPillView ?: return
        dockedRight = dockRight
        pill.setDockedRight(dockRight)

        container.removeAllViews()
        val pillParams =
            LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                .apply {
                    if (dockRight) {
                        marginEnd = dp(8)
                    } else {
                        marginStart = dp(8)
                    }
                }
        if (dockRight) {
            container.addView(pill, pillParams)
            container.addView(vBubble)
        } else {
            container.addView(vBubble)
            container.addView(pill, pillParams)
        }
    }

    private fun installDragAndTapListener(
        handleView: View,
        containerView: View,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false
        val slop = dp(8)

        handleView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
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
                        val metrics = resources.displayMetrics
                        val maxX = (metrics.widthPixels - containerView.width).coerceAtLeast(0)
                        val maxY =
                            (metrics.heightPixels - containerView.height - dp(48)).coerceAtLeast(0)
                        params.x = (startX + dx).coerceIn(0, maxX)
                        params.y = (startY + dy).coerceIn(dp(36), maxY)
                        runCatching { wm.updateViewLayout(containerView, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    if (!dragged && event.actionMasked == MotionEvent.ACTION_UP) {
                        v.performClick()
                        onBubbleTapped()
                    } else if (dragged) {
                        snapToNearestEdge(containerView, params, wm)
                    }
                    true
                }
                else -> false
            }
        }
    }

    // Modelled after Wispr Flow's `FloatingBarKt$snapToEdge` + `FlowScreenLayoutManager`:
    // determines whether the bubble was released in the left or right half of the screen
    // and springs it smoothly to that edge.
    private fun snapToNearestEdge(
        containerView: View,
        params: WindowManager.LayoutParams,
        wm: WindowManager,
    ) {
        val screenWidth = resources.displayMetrics.widthPixels
        val edgeMargin = dp(12)
        val centerX = params.x + containerView.width / 2
        val shouldDockRight = centerX > screenWidth / 2
        if (shouldDockRight != dockedRight) {
            rebuildDockOrder(shouldDockRight)
        }
        val targetX =
            if (shouldDockRight) {
                (screenWidth - containerView.width - edgeMargin).coerceAtLeast(edgeMargin)
            } else {
                edgeMargin
            }
        val fromX = params.x
        if (fromX == targetX) return

        snapAnimator?.cancel()
        snapAnimator =
            ValueAnimator.ofInt(fromX, targetX).apply {
                duration = 240L
                interpolator = OvershootInterpolator(0.9f)
                addUpdateListener { anim ->
                    params.x = anim.animatedValue as Int
                    runCatching { wm.updateViewLayout(containerView, params) }
                }
                start()
            }
    }

    // Same reflection flag (`PRIVATE_FLAG_NO_MOVE_ANIMATION = 64`) used by Wispr
    // Flow's `OverlayAnimationSuppressor` so OEM window managers do not add lag
    // to `updateViewLayout` during drags or pill expansion.
    private fun suppressMoveAnimation(params: WindowManager.LayoutParams) {
        runCatching {
            val field = WindowManager.LayoutParams::class.java.getField("privateFlags")
            field.setInt(params, field.getInt(params) or 64)
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
                .setSmallIcon(R.drawable.ic_launcher_foreground)
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
