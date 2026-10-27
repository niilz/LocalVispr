package dev.localvispr.text

// ==============================================================================
// Context-based writing styles (replicated from Wispr Flow's `StyleCategory`
// and `WritingStyle`)
// ==============================================================================

enum class WritingStyle(val id: String, val displayName: String, val subtitle: String) {
    FORMAL(
        id = "formal",
        displayName = "Formal",
        subtitle = "Caps + Punctuation (vollständige Sätze, korrekte Zeichensetzung)",
    ),
    CASUAL(
        id = "casual",
        displayName = "Casual",
        subtitle = "Natürlich & entspannt (angenehmer Umgangston)",
    ),
    VERY_CASUAL(
        id = "veryCasual",
        displayName = "Very Casual",
        subtitle = "Locker & direkt (minimalistische Zeichensetzung für Chats)",
    ),
    EXCITED(
        id = "excited",
        displayName = "Excited",
        subtitle = "Lebendig & dynamisch (energiegeladener Ton)",
    );

    fun promptInstruction(language: Language): String =
        when (language) {
            Language.GERMAN ->
                when (this) {
                    FORMAL ->
                        "Verwende einen formellen, professionellen Tonfall mit vollständigen Sätzen und präziser Zeichensetzung."
                    CASUAL ->
                        "Halte den Text in einem natürlichen, entspannten und freundlichen Umgangston."
                    VERY_CASUAL ->
                        "Formatiere sehr locker wie in einem schnellen Chat; keine übertriebene Zeichensetzung, kurze direkte Phrasen."
                    EXCITED ->
                        "Formuliere lebendig, enthusiastisch und ausdrucksstark mit dynamischem Satzbau."
                }
            Language.ENGLISH ->
                when (this) {
                    FORMAL ->
                        "Use a formal, professional tone with complete sentences and precise punctuation."
                    CASUAL -> "Keep the text natural, relaxed, and conversational."
                    VERY_CASUAL ->
                        "Format casually for quick messaging; keep punctuation minimal and phrasing direct."
                    EXCITED ->
                        "Express the thought with energetic, lively, and enthusiastic phrasing."
                }
        }
}

enum class StyleCategory(
    val id: String,
    val tabLabel: String,
    val description: String,
    val defaultStyle: WritingStyle,
    val sampleApps: String,
) {
    PERSONAL(
        id = "personal",
        tabLabel = "Personal",
        description =
            "Gilt in Consumer-Messengern (WhatsApp, Telegram, Signal, Messages, Instagram)",
        defaultStyle = WritingStyle.CASUAL,
        sampleApps = "WhatsApp, Telegram, Signal, SMS",
    ),
    WORK(
        id = "work",
        tabLabel = "Work",
        description = "Gilt in Business- & Projekt-Tools (Slack, MS Teams, Notion, Asana, Jira)",
        defaultStyle = WritingStyle.FORMAL,
        sampleApps = "Slack, Teams, Notion, Jira",
    ),
    EMAIL(
        id = "email",
        tabLabel = "Email",
        description = "Gilt in E-Mail-Clients (Gmail, Outlook, Spark, Thunderbird)",
        defaultStyle = WritingStyle.FORMAL,
        sampleApps = "Gmail, Outlook, Mail",
    ),
    OTHER(
        id = "other",
        tabLabel = "Other",
        description = "Gilt in allen übrigen Apps (Browser, Notizen, Office, Dokumente)",
        defaultStyle = WritingStyle.FORMAL,
        sampleApps = "Browser, Notizen, Docs, Suche",
    );

    companion object {
        private val MESSENGER_PACKAGES =
            setOf(
                "com.whatsapp",
                "org.telegram.messenger",
                "org.thoughtcrime.securesms", // Signal
                "com.google.android.apps.messaging",
                "com.facebook.orca", // Messenger
                "com.instagram.android",
                "com.discord",
                "ch.threema.app",
                "com.viber.voip",
            )

        private val WORK_PACKAGES =
            setOf(
                "com.Slack",
                "com.microsoft.teams",
                "notion.id",
                "com.asana.app",
                "com.atlassian.android.jira.core",
                "com.trello",
                "com.linear",
                "com.github.android",
            )

        private val EMAIL_PACKAGES =
            setOf(
                "com.google.android.gm",
                "com.microsoft.office.outlook",
                "com.readdle.spark",
                "net.thunderbird.android",
                "com.fsck.k9",
                "com.samsung.android.email.provider",
            )

        fun categoryForPackage(packageName: String?): StyleCategory {
            if (packageName == null) return OTHER
            val pkg = packageName.lowercase()
            return when {
                MESSENGER_PACKAGES.any { pkg.startsWith(it.lowercase()) } -> PERSONAL
                WORK_PACKAGES.any { pkg.startsWith(it.lowercase()) } -> WORK
                EMAIL_PACKAGES.any { pkg.startsWith(it.lowercase()) } -> EMAIL
                else -> OTHER
            }
        }
    }
}
