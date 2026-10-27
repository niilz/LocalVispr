package dev.localvispr.onboarding

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class OnboardingStateTest {

    @Test
    fun initialStepIsIntro() {
        val flow = OnboardingFlow()
        assertEquals(OnboardingStep.INTRO, flow.currentStep)
        assertEquals(0, flow.currentStep.index)
        assertEquals(4, OnboardingStep.TOTAL_STEPS)
    }

    @Test
    fun navigationMovesForwardAndBackward() {
        val flow = OnboardingFlow()

        assertTrue(flow.advance())
        assertEquals(OnboardingStep.PLAYGROUND, flow.currentStep)

        assertTrue(flow.advance())
        assertEquals(OnboardingStep.PERMISSIONS, flow.currentStep)

        assertTrue(flow.advance())
        assertEquals(OnboardingStep.READY, flow.currentStep)

        // Cannot advance past the final step
        assertFalse(flow.advance())
        assertEquals(OnboardingStep.READY, flow.currentStep)

        // Move backward
        assertTrue(flow.back())
        assertEquals(OnboardingStep.PERMISSIONS, flow.currentStep)

        assertTrue(flow.back())
        assertEquals(OnboardingStep.PLAYGROUND, flow.currentStep)

        assertTrue(flow.back())
        assertEquals(OnboardingStep.INTRO, flow.currentStep)

        // Cannot go back before INTRO
        assertFalse(flow.back())
        assertEquals(OnboardingStep.INTRO, flow.currentStep)
    }

    @Test
    fun jumpToSpecificStep() {
        val flow = OnboardingFlow()
        flow.goTo(OnboardingStep.PERMISSIONS)
        assertEquals(OnboardingStep.PERMISSIONS, flow.currentStep)
    }

    @Test
    fun accessibilityInstructionStepsMatchReferenceFindings() {
        // Based on the reference APK analysis:
        // Step 1: Open downloaded apps
        // Step 2: Toggle on service
        // Step 3: Explicit warning not to enable system shortcut
        val steps = AccessibilityGuidance.steps
        assertEquals(3, steps.size)
        assertTrue(steps[0].instruction.contains("Heruntergeladene Apps"))
        assertTrue(steps[1].instruction.contains("LocalVispr"))
        assertTrue(steps[2].isWarning)
        assertTrue(steps[2].instruction.contains("Verknüpfung"))
    }
}
