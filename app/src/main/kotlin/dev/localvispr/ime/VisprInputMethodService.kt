package dev.localvispr.ime

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import dev.localvispr.AppContainer
import dev.localvispr.R
import dev.localvispr.audio.VoiceRecorder
import dev.localvispr.service.CyberpunkVBubbleView
import dev.localvispr.service.DictationState
import dev.localvispr.service.WaveformPillView
import dev.localvispr.text.TranscriptPostProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==============================================================================
// On-device Voice Input Method Service (IME)
// ==============================================================================
//
// Provides a dedicated cyberpunk keyboard input view for voice dictation.
// Tapping the central Cyberpunk-V bubble captures speech via VoiceRecorder,
// while live audio levels animate the WaveformPillView. Once dictation completes,
// the audio is transcribed via SherpaWhisperTranscriber and cleaned with
// TranscriptPostProcessor before being committed directly into the focused
// input field via currentInputConnection.

class VisprInputMethodService : InputMethodService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val recorder = VoiceRecorder()
    private var dictationState = DictationState.IDLE

    private var bubbleView: CyberpunkVBubbleView? = null
    private var waveformPillView: WaveformPillView? = null

    override fun onCreate() {
        super.onCreate()
        // Feed real-time microphone energy levels to the waveform equalizer pill.
        scope.launch {
            recorder.audioLevel.collect { level -> waveformPillView?.setAudioLevel(level) }
        }
    }

    override fun onCreateInputView(): View {
        val root =
            FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#0B0714"))
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220))
            }

        // Keyboard picker button allows quickly returning to the primary virtual keyboard.
        val switchButton =
            Button(this).apply {
                text = "⌨"
                textSize = 20f
                setTextColor(Color.parseColor("#6EE7F9"))
                setBackgroundColor(Color.TRANSPARENT)
                val pad = dp(8)
                setPadding(pad, pad, pad, pad)
                layoutParams =
                    FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                        )
                        .apply {
                            gravity = Gravity.BOTTOM or Gravity.START
                            setMargins(dp(12), 0, 0, dp(12))
                        }
                setOnClickListener {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showInputMethodPicker()
                }
            }

        val centerColumn =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams =
                    FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                        )
                        .apply { gravity = Gravity.CENTER }
            }

        val waveform =
            WaveformPillView(this).apply {
                setDictationState(dictationState)
                layoutParams =
                    LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                        .apply { bottomMargin = dp(16) }
            }
        waveformPillView = waveform

        val bubble =
            CyberpunkVBubbleView(this).apply {
                setDictationState(dictationState)
                setOnClickListener { onBubbleClicked() }
            }
        bubbleView = bubble

        centerColumn.addView(waveform)
        centerColumn.addView(bubble)

        root.addView(switchButton)
        root.addView(centerColumn)

        return root
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (recorder.isRecording) {
            recorder.cancel()
            setState(DictationState.IDLE)
        }
    }

    override fun onDestroy() {
        if (recorder.isRecording) {
            recorder.cancel()
        }
        scope.cancel()
        bubbleView = null
        waveformPillView = null
        super.onDestroy()
    }

    private fun onBubbleClicked() {
        when (dictationState) {
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
            return
        }
        if (recorder.start()) {
            setState(DictationState.LISTENING)
        } else {
            Toast.makeText(this, R.string.toast_mic_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishDictation() {
        if (dictationState != DictationState.LISTENING) return
        setState(DictationState.PROCESSING)

        val container = AppContainer.get(this)
        val settings = container.settings.state.value
        val surrounding = currentInputConnection?.getTextBeforeCursor(160, 0)?.toString()

        scope.launch {
            val cleaned =
                withContext(Dispatchers.Default) {
                    val samples = recorder.stopAndTakeSamples()
                    if (samples.isEmpty()) return@withContext ""
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
                currentInputConnection?.commitText(cleaned, 1)
            }
        }
    }

    private fun setState(next: DictationState) {
        dictationState = next
        bubbleView?.setDictationState(next)
        waveformPillView?.setDictationState(next)
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                resources.displayMetrics,
            )
            .toInt()
}
