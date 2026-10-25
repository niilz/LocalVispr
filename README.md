<p align="center">
  <img src="docs/logo.png" alt="LocalVispr Logo" width="128" />
</p>

<h1 align="center">LocalVispr</h1>

<p align="center">
  <strong>100 % lokales, intelligentes Smart-Dictation Tool für Android (Deutsch & Englisch)</strong><br>
  Angelehnt an den Floating-Bubble- und Accessibility-Injektions-Workflow von Wispr Flow – vollständig offline, ohne Cloud-Aufrufe, ohne Benutzerkonto und ohne kostenpflichtige API-Tokens.
</p>

---

## Features

- **100 % On-Device & Datenschutzfreundlich:** Weder Audioaufnahmen noch Transkripte verlassen das Gerät. Spracherkennung und Bereinigung arbeiten vollständig offline.
- **Schwebende Cyberpunk-Blase (Floating Overlay):**
  - Frei verschiebbare Kapsel mit neon-metallischem "V"-Logo (`TYPE_APPLICATION_OVERLAY`), die sich beim Diktieren magnetisch am Bildschirmrand dockt.
  - Dehnt sich während der Aufnahme zu einer horizontalen Kapsel mit animiertem Waveform-Equalizer und Live-Pegelausschlag aus.
  - Schneller Abbruch (`✕`) oder Bestätigen per Fingertipp.
- **Intelligente Sichtbarkeit (Auto-Show / Auto-Hide):**
  - Die Blase blendet sich über die `BubbleVisibilityPolicy` automatisch nur ein, wenn ein bearbeitbares Textfeld fokussiert oder die Bildschirmtastatur aktiv ist.
- **Systemweite Text-Injektion:**
  - Schreibt diktierten Text direkt in fremde Apps via `AccessibilityService.getInputMethod().getCurrentInputConnection().commitText(...)` (Android 13+).
  - Robuste Fallbacks auf `ACTION_SET_TEXT` und die Zwischenablage (`setPrimaryClip`).
- **Zweistufige Textglättung:**
  - _Stufe 1 (Deterministischer Filter):_ Schneller, latenzfreier Filter für deutsche und englische Füllwörter (_ähm_, _äh_, _um_, _uh_, _halt_, etc.), Stotter-Deduplizierung und Zeichensetzungs-Korrektur (`FillerWordFilter`).
  - _Stufe 2 (Optionales On-Device LLM):_ Tiefere semantische Glättung via Google LiteRT-LM (z. B. SmolLM2 135M oder Qwen 0.6B) mit Anti-Halluzinations-Schutz (`TranscriptPostProcessor`).
- **Kontext-sensitives Diktieren (`EditorContext`):**
  - Liest Text vor und nach dem Cursor (`getSurroundingText`) aus, um Anschlüsse, Satzanfänge und Groß-/Kleinschreibung automatisch an den bestehenden Text anzupassen.
- **Diktat-Stile & Tonalität (`DictationStyle`):**
  - Umschaltbare Modi für unterschiedliche Anwendungsbereiche: _Standard_, _Formell_, _Locker_, _Code_ und _Aufzählung_.
- **Persönliches Wörterbuch & Hotwords (`hotwords`):**
  - Eigene Fachbegriffe, Eigennamen und Akronyme hinterlegen, damit Whisper und das LLM sie bevorzugt und orthografisch exakt erfassen.
- **Hardware & Sensorik:**
  - _Bluetooth- & Headset-Routing (`AudioRouteMonitor`):_ Dynamisches Umschalten auf verbundene Bluetooth-Headsets (SCO/HFP) oder kabelgebundene Mikrofone über den `AudioManager`.
  - _Haptisches Feedback (`BubbleHaptics`):_ Taktile Vibrationsbestätigungen beim Start/Stopp der Aufnahme und nach erfolgreicher Textinjektion.
  - _Schüttel-Geste (`ShakeDetector`):_ Optionales Starten der Aufnahme durch kurzes Schütteln des Smartphones (Beschleunigungssensor).
- **Lokale Diktat-Historie (`DictationHistoryStore`):**
  - Lokales Archiv früherer Transkripte zum schnellen Nachlesen, Durchsuchen und erneuten Kopieren.
- **Modell-Katalog & On-Device Download-Manager:**
  - Verwaltet Offline-Modelle direkt in der App (`ModelCatalog`, `ModelRepository`, `ModelDownloader`), prüft Speicherplatz und erlaubt den Download sowie das Wechseln und Löschen von Modellen.

---

## Architektur

| Schicht                | Technologie                                                                                            | Quellpfad                                                                                                |
| ---------------------- | ------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| **Trigger & Overlay**  | Frei ziehbare Overlay-Blase (`TYPE_APPLICATION_OVERLAY`) im Foreground-Service mit Waveform-Pill       | `service/FloatingBubbleService.kt`<br>`service/CyberpunkVBubbleView.kt`<br>`service/WaveformPillView.kt` |
| **Sichtbarkeit**       | Intelligente Steuerung nach Tastatur-/Fokusstatus & Snooze                                             | `service/BubbleVisibilityPolicy.kt`                                                                      |
| **Texteinfügung**      | Android 13+ `AccessibilityInputConnection.commitText(...)`, Fallback `ACTION_SET_TEXT`, Zwischenablage | `accessibility/VisprAccessibilityService.kt`                                                             |
| **Audio-Aufnahme**     | 16 kHz Mono 16-Bit PCM (`AudioRecord`) mit Audio-Routing & Pegelmessung                                | `audio/VoiceRecorder.kt`<br>`audio/AudioRouteMonitor.kt`                                                 |
| **Sensorik & Haptik**  | Accelerometer-Schüttelerkennung & Vibrator-Feedback                                                    | `sensors/ShakeDetector.kt`<br>`service/BubbleHaptics.kt`                                                 |
| **Offline STT**        | Sherpa-ONNX `OfflineRecognizer` mit Whisper Tiny / Base (int8, mehrsprachig DE/EN)                     | `stt/SherpaWhisperTranscriber.kt`                                                                        |
| **Glättung (Stufe 1)** | Deterministischer Filter für Füllwörter, Stottern & Satzzeichen                                        | `text/FillerWordFilter.kt`                                                                               |
| **Glättung (Stufe 2)** | Optionales On-Device LLM via Google LiteRT-LM mit Halluzinationsschutz                                 | `text/TranscriptPostProcessor.kt`<br>`llm/LiteRtLlmEngine.kt`                                            |
| **Kontext & Stile**    | Umfeld-Kontext (`getSurroundingText`), Tonalitätsstile & Hotwords                                      | `text/EditorContext.kt`<br>`text/DictationStyle.kt`                                                      |
| **Historie**           | Lokale Speicherung und Suche früherer Transkripte                                                      | `history/DictationHistoryStore.kt`                                                                       |
| **Modellverwaltung**   | Lokaler Katalog, Download und Umschalten von STT- und LLM-Modellen                                     | `models/ModelCatalog.kt`<br>`models/ModelRepository.kt`<br>`models/ModelDownloader.kt`                   |

---

## Bauen & Installieren

### Voraussetzungen

- Android SDK (empfohlen: API 36 / Android 16 Preview oder kompatibel ab Min-SDK 33)
- Java Development Kit (JDK) 21

### APK kompilieren

```bash
./gradlew assembleDebug
```

Die fertige APK liegt anschließend unter:  
`app/build/outputs/apk/debug/app-debug.apk`

### Auf Android-Gerät / Emulator installieren

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Berechtigungen per ADB erteilen (optional für schnelles Testen)

```bash
# Accessibility Service aktivieren
adb shell settings put secure enabled_accessibility_services \
  dev.localvispr/dev.localvispr.accessibility.VisprAccessibilityService

# Overlay-Berechtigung (Über anderen Apps anzeigen)
adb shell appops set dev.localvispr SYSTEM_ALERT_WINDOW allow

# Mikrofon-Berechtigung
adb shell pm grant dev.localvispr android.permission.RECORD_AUDIO
```

---

## Tests & Code-Qualität

Unit-Tests und Formatierungsprüfungen können direkt über Gradle ausgeführt werden:

```bash
# Formatierung prüfen / anwenden
./gradlew spotlessApply

# Unit-Tests ausführen
./gradlew testDebugUnitTest
```

Zusätzlich stehen leichtgewichtige, isolierte Testskripte zur Verfügung:

```bash
./tools/jvm-tests/run.sh
./tools/dev/ktfmt.sh
./tools/dev/shellcheck.sh
```

---

## Weiterführende Dokumentation

- [`docs/reference-analysis.md`](docs/reference-analysis.md): Detaillierte Analyse der Architektur-Muster der Vorlage (Wispr Flow).
- [`TODO.md`](TODO.md): Priorisierte Feature-Roadmap und anstehende Aufgaben.

---

## Lizenz

Dieses Projekt ist lizenziert unter der [Apache License, Version 2.0](LICENSE).
