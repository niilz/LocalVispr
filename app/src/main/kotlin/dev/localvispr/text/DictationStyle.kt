package dev.localvispr.text

enum class DictationStyle(val label: String) {
    CASUAL("Locker / Chat"),
    FORMAL("Formell / E-Mail"),
    CODE_PROMPT("Code & Prompt"),
    BULLET_POINTS("Stichpunkte");

    fun promptInstruction(language: Language): String =
        when (this) {
            CASUAL ->
                when (language) {
                    Language.GERMAN ->
                        "Schreibe in einem lockeren, natürlichen Umgangston für Chats und Messenger."
                    Language.ENGLISH ->
                        "Write in a casual, natural conversational tone suitable for chat and messaging."
                }
            FORMAL ->
                when (language) {
                    Language.GERMAN ->
                        "Formuliere den Text professionell, höflich und grammatikalisch einwandfrei für formelle geschäftliche E-Mails oder Dokumente."
                    Language.ENGLISH ->
                        "Formulate the text professionally, politely, and grammatically polished for formal business emails or documents."
                }
            CODE_PROMPT ->
                when (language) {
                    Language.GERMAN ->
                        "Behalte technische Bezeichner, Code-Syntax, Pfade und Variablennamen exakt bei. Optimiere den Text als präzisen Prompt oder Programmierbefehl."
                    Language.ENGLISH ->
                        "Preserve technical identifiers, code syntax, paths, and variable names exactly. Format the text as a precise prompt or coding instruction."
                }
            BULLET_POINTS ->
                when (language) {
                    Language.GERMAN ->
                        "Formatiere den Inhalt als prägnante Stichpunkte mit vorangestellten Bindestrichen (- )."
                    Language.ENGLISH ->
                        "Format the content as concise bullet points starting with dashes (- )."
                }
        }
}
