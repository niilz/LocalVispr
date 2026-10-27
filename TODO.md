# LocalVispr – Priorisierte Feature- & TODO-Liste

Diese Liste wurde per paarweisem Vergleich im Vergleich zur Referenz-App (_Wispr Flow v2.8.3_) priorisiert:

## Priorisierte Roadmap

- [x] **1. Auto-Show / Auto-Hide der Blase**
  - Blase nur dann auf dem Bildschirm anzeigen, wenn ein bearbeitbares Textfeld fokussiert oder die Bildschirmtastatur aktiv ist (`SoftKeyboardStateTracker`, `FocusedNodeFinder` im Accessibility Service).
- [x] **2. Umfeld-Kontext (`ax_context`)**
  - Vor der Glättung umliegenden Text im fokussierten Feld via `getSurroundingText(...)` sowie die aktive Ziel-App auslesen und an das LLM übergeben (für Groß-/Kleinschreibung, Satzanschlüsse und Fachkontext).
- [ ] **3. Bluetooth- & Headset-Routing**
  - Entfällt: Androids Audio-Framework routet verbundene Bluetooth- (SCO/BLE) und Kabel-Headsets bei `AudioSource.MIC` bereits nativ und automatisch. Wispr Flow verzichtet ebenfalls auf erzwungenes `setCommunicationDevice`, um Konflikte mit Browser- und Media-Apps zu vermeiden.
- [x] **4. Live-Transkription (Streaming)**
  - Während der Aufnahme periodisch Zwischenstände transkribieren und wahlweise als Overlay-Vorschau oder direkt via `commitText(...)` / `deleteSurroundingText(...)` im Ziel-Feld aktualisieren.
- [x] **5. Stil- und Tonalitäts-Profile**
  - Wählbare Modi für die Bereinigung (z. B. _Casual/Chat_, _Formal/E-Mail_, _Code/Prompt_, _Bullet Points_).
- [x] **6. Persönliches Wörterbuch (`dictionary_words`)**
  - Möglichkeit für den Nutzer, eigene Fachbegriffe, Eigennamen und Abkürzungen zu hinterlegen, damit Whisper und das LLM diese bevorzugt und korrekt schreiben.
- [x] **7. Haptisches Feedback**
  - Taktile Vibrationsbestätigung beim Starten, Stoppen der Aufnahme und nach erfolgreicher Textinjektion.
- [x] **8. Schüttel-Geste (Shake-to-Dictate)**
  - Optionales Triggern der Aufnahme durch kurzes Schütteln des Smartphones (`ShakeDetector` via Accelerometer).
- [x] **9. Diktat-Historie**
  - Lokale Room-Datenbank zur Speicherung vergangener Diktate mit Such- und Kopierfunktion.
- [x] **10. Snooze-Funktion**
  - Schnelles temporäres Ausblenden / Stummschalten der Blase (z. B. für 1 Stunde oder bis zum nächsten App-Wechsel).
- [x] **11. Live-Audio-Pegel / Wellenform-Animation**
  - Visuelles Waveform-Feedback auf der schwebenden Blase während `DictationState.LISTENING`.

---

## Zusätzliche technische Aufgaben

- [x] **Optionale eigene Tastatur (`InputMethodService` / IME):** Zweiter Auslöser und nativer composing text.
- [x] **R8 / ProGuard Keep-Regeln:** Release-Verifizierung für native JNI-Klassen (`sherpa-onnx`, `litertlm-android`).
