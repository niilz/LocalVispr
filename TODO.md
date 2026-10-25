# LocalVispr – Feature- & TODO-Liste

## Geplante Features

- [ ] **Live-Transkription & dynamische Text-Korrektur während des Sprechens**
  - **Variante A (Overlay-Vorschau):** Während der Aufnahme periodisch (~1–1,5 s) das bisherige Audio-Fenster transkribieren und den wachsenden Text zusammen mit einem Live-Mikrofonpegel direkt in/an der schwebenden Blase anzeigen; beim Stoppen den final geglätteten Text einfügen.
  - **Variante B (Live im Ziel-Textfeld mit Korrektur):** Zwischenstände sofort per `AccessibilityInputConnection.commitText(...)` ins fokussierte Eingabefeld schreiben und bei kontextbezogenen Korrekturen oder dem finalen LLM-Durchlauf das geänderte Suffix per `deleteSurroundingText(beforeLength, 0)` + `commitText(...)` ersetzen.
- [x] **Live-Audio-Pegel / Wellenform in der Blase**
  - Visuelles Feedback während `DictationState.LISTENING` (analog zu `DictationPhase.Dictating(audioLevel)` in der Referenz-App), damit man sofort sieht, ob das Mikrofon Signal bekommt.
- [ ] **Optionale eigene Tastatur (`InputMethodService` / IME)**
  - Zweiter Auslöser neben der schwebenden Blase; ermöglicht außerdem natives `InputConnection.setComposingText(...)` (unterstrichener Live-Text während des Diktierens, der sich rückwirkend glätten lässt).
- [ ] **Kontext-abhängige Stil-Vorgaben (Personalization Styles)**
  - Unterschiedliche Bereinigungs-Stile je nach Ziel-App oder Auswahl (z. B. Chat/Locker, E-Mail/Formell, Code/Prompt).
- [ ] **Automatisches Ein-/Ausblenden der Blase**
  - Blase optional nur dann anzeigen, wenn `VisprAccessibilityService.hasEditableFocus` erkennt, dass ein Textfeld fokussiert bzw. die Bildschirmtastatur offen ist.
- [ ] **R8 / ProGuard-Regeln für Release-Builds**
  - Keep-Regeln für die JNI-Klassen von `sherpa-onnx` und `litertlm-android` verifizieren und `isMinifyEnabled = true` aktivieren.
