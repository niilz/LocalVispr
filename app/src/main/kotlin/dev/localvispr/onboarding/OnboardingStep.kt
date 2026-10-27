package dev.localvispr.onboarding

/**
 * Onboarding steps for LocalVispr, inspired by the reference APK (Wispr Flow) but streamlined to
 * omit cloud accounts, telemetry, and external surveys.
 */
enum class OnboardingStep(val index: Int, val title: String) {
    INTRO(0, "Vision & Datenschutz"),
    PLAYGROUND(1, "Live ausprobieren"),
    PERMISSIONS(2, "Berechtigungen einrichten"),
    READY(3, "Startklar!");

    val hasNext: Boolean
        get() = index < TOTAL_STEPS - 1

    val hasPrevious: Boolean
        get() = index > 0

    companion object {
        const val TOTAL_STEPS = 4

        fun fromIndex(idx: Int): OnboardingStep = entries.firstOrNull { it.index == idx } ?: INTRO
    }
}

/**
 * Guidance step for the Android Accessibility Service configuration. Extracted directly from
 * reverse-engineering the reference APK pattern.
 */
data class AccessibilityInstruction(
    val stepNumber: Int,
    val title: String,
    val instruction: String,
    val isWarning: Boolean = false,
)

object AccessibilityGuidance {
    val steps =
        listOf(
            AccessibilityInstruction(
                stepNumber = 1,
                title = "1. Apps / Dienste suchen",
                instruction =
                    "Öffne „Heruntergeladene Apps“ (oder „Installierte Dienste“) in den Einstellungen.",
            ),
            AccessibilityInstruction(
                stepNumber = 2,
                title = "2. Dienst einschalten",
                instruction =
                    "Tippe auf „LocalVispr Text-Einfügung“ und aktiviere den oberen Schalter.",
            ),
            AccessibilityInstruction(
                stepNumber = 3,
                title = "3. ⚠️ Verknüpfung auslassen!",
                instruction =
                    "Schalte die „LocalVispr-Verknüpfung / Shortcut“ NICHT ein. Sie erzeugt nur ein störendes graues System-Icon.",
                isWarning = true,
            ),
        )
}

/** State machine managing progression through the onboarding flow. */
class OnboardingFlow(initialStep: OnboardingStep = OnboardingStep.INTRO) {

    var currentStep: OnboardingStep = initialStep
        private set

    fun advance(): Boolean {
        return if (currentStep.hasNext) {
            currentStep = OnboardingStep.fromIndex(currentStep.index + 1)
            true
        } else {
            false
        }
    }

    fun back(): Boolean {
        return if (currentStep.hasPrevious) {
            currentStep = OnboardingStep.fromIndex(currentStep.index - 1)
            true
        } else {
            false
        }
    }

    fun goTo(step: OnboardingStep) {
        currentStep = step
    }
}
