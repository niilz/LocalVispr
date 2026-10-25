# LocalVispr

100 % local Android dictation tool (German & English) modelled after the
floating-bubble + accessibility text-injection workflow of Wispr Flow, without
any cloud calls, user accounts, or paid API tokens.

## Architecture

| Layer | Technology | Source |
|---|---|---|
| Trigger | Draggable overlay bubble (`TYPE_APPLICATION_OVERLAY`) in a microphone foreground service | `service/FloatingBubbleService.kt` |
| Text injection | Android 13+ `AccessibilityService.getInputMethod().getCurrentInputConnection().commitText(...)`, fallback to `ACTION_SET_TEXT`, then clipboard | `accessibility/VisprAccessibilityService.kt` |
| Audio capture | 16 kHz mono 16-bit PCM (`AudioRecord`) with RMS silence gate | `audio/VoiceRecorder.kt`, `audio/PcmSamples.kt` |
| Offline STT | Sherpa-ONNX `OfflineRecognizer` with Whisper Tiny / Base (int8, multilingual) | `stt/SherpaWhisperTranscriber.kt` |
| Cleanup (stage 1) | Deterministic DE/EN hesitation, stutter, and punctuation filter | `text/FillerWordFilter.kt` |
| Cleanup (stage 2) | Optional on-device LLM via Google LiteRT-LM (SmolLM2 135M / Qwen3 0.6B int4) with anti-hallucination guard | `text/TranscriptPostProcessor.kt`, `llm/LiteRtLlmEngine.kt` |
| Model switching | Checks on-device availability, prompts to download the small default on first launch, and lets the user switch/delete models anytime | `models/ModelCatalog.kt`, `models/ModelRepository.kt`, `models/ModelDownloader.kt` |

See [`docs/reference-analysis.md`](docs/reference-analysis.md) for the findings
from the reference APK.

## Building on the host

The dev container's firewall blocks `dl.google.com`, `repo.maven.apache.org`,
`services.gradle.org`, and `github.com`, so the full Android APK must be built
on the host (Android Studio or CLI with an Android SDK for API 36):

```bash
./gradlew spotlessApply testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installation, enable the accessibility service quickly via ADB if desired:

```bash
adb shell settings put secure enabled_accessibility_services \
  dev.localvispr/dev.localvispr.accessibility.VisprAccessibilityService
adb shell appops set dev.localvispr SYSTEM_ALERT_WINDOW allow
adb shell pm grant dev.localvispr android.permission.RECORD_AUDIO
```

## Running tests & formatting inside the dev container

Pure-Kotlin unit tests, `ktfmt` (same `kotlinlang` style as Spotless), and
ShellCheck (WASM) run completely offline from Maven via npm packages:

```bash
./tools/jvm-tests/run.sh
./tools/dev/ktfmt.sh
./tools/dev/shellcheck.sh
```
