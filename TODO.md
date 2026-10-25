# LocalVispr – Priorisierte Feature- & TODO-Liste

Diese Liste wurde per paarweisem Vergleich im Vergleich zur Referenz-App (_Wispr Flow v2.8.3_) priorisiert:

## Priorisierte Roadmap

- [ ] **1. Auto-Show / Auto-Hide der Blase**
  - Blase nur dann auf dem Bildschirm anzeigen, wenn ein bearbeitbares Textfeld fokussiert oder die Bildschirmtastatur aktiv ist (`SoftKeyboardStateTracker`, `FocusedNodeFinder` im Accessibility Service).
- [ ] **2. Umfeld-Kontext (`ax_context`)**
  - Vor der Glättung umliegenden Text im fokussierten Feld via `getSurroundingText(...)` sowie die aktive Ziel-App auslesen und an das LLM übergeben (für Groß-/Kleinschreibung, Satzanschlüsse und Fachkontext).
- [ ] **3. Bluetooth- & Headset-Routing (`AudioDeviceCallback`)**
  - Dynamisches Routing des Audio-Inputs auf verbundene Bluetooth-Headsets (SCO/HFP) oder kabelgebundene Mikrofone über den `AudioManager`.
- [ ] **4. Live-Transkription (Streaming)**
  - Während der Aufnahme periodisch Zwischenstände transkribieren und wahlweise als Overlay-Vorschau oder direkt via `commitText(...)` / `deleteSurroundingText(...)` im Ziel-Feld aktualisieren.
- [ ] **5. Stil- und Tonalitäts-Profile**
  - Wählbare Modi für die Bereinigung (z. B. _Casual/Chat_, _Formal/E-Mail_, _Code/Prompt_, _Bullet Points_).
- [ ] **6. Persönliches Wörterbuch (`dictionary_words`)**
  - Möglichkeit für den Nutzer, eigene Fachbegriffe, Eigennamen und Abkürzungen zu hinterlegen, damit Whisper und das LLM diese bevorzugt und korrekt schreiben.
- [ ] **7. Haptisches Feedback**
  - Taktile Vibrationsbestätigung beim Starten, Stoppen der Aufnahme und nach erfolgreicher Textinjektion.
- [ ] **8. Schüttel-Geste (Shake-to-Dictate)**
  - Optionales Triggern der Aufnahme durch kurzes Schütteln des Smartphones (`ShakeDetector` via Accelerometer).
- [ ] **9. Diktat-Historie**
  - Lokale Room-Datenbank zur Speicherung vergangener Diktate mit Such- und Kopierfunktion.
- [ ] **10. Snooze-Funktion**
  - Schnelles temporäres Ausblenden / Stummschalten der Blase (z. B. für 1 Stunde oder bis zum nächsten App-Wechsel).
- [ ] **11. Live-Audio-Pegel / Wellenform-Animation**
  - Visuelles Waveform-Feedback auf der schwebenden Blase während `DictationState.LISTENING`.

---

## Zusätzliche technische Aufgaben

- [ ] **Optionale eigene Tastatur (`InputMethodService` / IME):** Zweiter Auslöser und nativer composing text.
- [ ] **R8 / ProGuard Keep-Regeln:** Release-Verifizierung für native JNI-Klassen (`sherpa-onnx`, `litertlm-android`).
