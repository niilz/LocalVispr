# Reference APK Analysis

Source: `com.wispr.flowapp` 2.8.3 (versionCode 182, minSdk 33, targetSdk 37),
pulled from `emulator-5554` into `tmp/apk/` and decompiled with the JADX core
API into `tmp/jadx-cls/` (neither is committed).

Only the *architecture patterns* built on public Android APIs are recorded here.
No proprietary code, prompts, or assets are copied into this project.

## 1. System integration and trigger

| Building block | Finding |
|---|---|
| Foreground service | `FlowService`, `foregroundServiceType="specialUse\|microphone"` |
| Overlay | Floating bubble/bar via `WindowManager.addView` with `TYPE_APPLICATION_OVERLAY` (2038), needs `SYSTEM_ALERT_WINDOW` |
| Accessibility service | `FlowAccessibilityService` / `InputAccessibilityService`, events: `typeWindowsChanged`, flags: `flagInputMethodEditor`, `flagRetrieveInteractiveWindows`, `flagRequestFilterKeyEvents`, `flagReportViewIds` |
| Bubble visibility | Shown when the accessibility service detects a focused editable field / visible soft keyboard (`SoftKeyboardStateTracker`, `FocusedNodeFinder`) |
| Not used | No IME (`BIND_INPUT_METHOD`) |

## 2. Text injection (in order of preference)

1. `AccessibilityService.getInputMethod().getCurrentInputConnection().commitText()`
   (API 33+, enabled by `flagInputMethodEditor`). This works without being the
   active keyboard.
2. Verification via `getSurroundingText(500, 500, 0)` before and after the commit.
3. Fallback: `AccessibilityNodeInfo.performAction(ACTION_SET_TEXT, …)` on the focused node.
4. Last resort: clipboard (`setPrimaryClip`).

## 3. Audio

- `AudioRecord` with `MediaRecorder.AudioSource.MIC`, 16 kHz (`SAMPLE_RATE_HZ = 16000`), PCM.

## 4. Prompting and post-processing

- Cleanup runs **server-side** (`TranscribeInteractor.transcribeGRPC`). The client
  contains no LLM prompts.
- The client sends context with each request: `ax_context` (surrounding text of the
  field), `dictionary_words`, `personalization_styles`/`styles`.
- This means our local cleanup prompt has to be designed from scratch. Useful
  inputs to replicate locally: the surrounding field text, a user dictionary,
  and a style setting.
