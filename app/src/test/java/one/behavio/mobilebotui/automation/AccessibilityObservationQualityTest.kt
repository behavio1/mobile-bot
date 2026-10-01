package one.behavio.mobilebotui.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityObservationQualityTest {
    @Test
    fun `semantic screen stays on accessibility`() {
        val result = assessAccessibilityObservation(metrics(readable = 12, actionable = 6, labeledActions = 5))

        assertEquals("sufficient", result.quality)
        assertFalse(result.supportRecommended)
        assertEquals("none", result.recommendedSupport)
    }

    @Test
    fun `truncated semantic tree asks for another accessibility read`() {
        val result = assessAccessibilityObservation(metrics(readable = 40, actionable = 8, labeledActions = 8, truncated = true))

        assertEquals("partial", result.quality)
        assertFalse(result.supportRecommended)
        assertEquals("accessibility_scroll_or_targeted_read", result.recommendedSupport)
    }

    @Test
    fun `empty semantic tree requests ocr then vision`() {
        val result = assessAccessibilityObservation(metrics(readable = 0, actionable = 0, labeledActions = 0))

        assertEquals("insufficient", result.quality)
        assertTrue(result.supportRecommended)
        assertEquals("ocr_then_vision", result.recommendedSupport)
        assertTrue("no_semantic_content" in result.reasons)
    }

    @Test
    fun `large unlabeled surface requests support even with some labels`() {
        val result = assessAccessibilityObservation(
            metrics(readable = 2, actionable = 1, labeledActions = 1, largeSurfaces = 1),
        )

        assertEquals("partial", result.quality)
        assertTrue(result.supportRecommended)
        assertEquals("ocr_then_vision", result.recommendedSupport)
        assertTrue("unlabeled_large_surface" in result.reasons)
    }

    @Test
    fun `unlabeled actions are reported when semantics are empty`() {
        val result = assessAccessibilityObservation(metrics(readable = 0, actionable = 4, labeledActions = 0))

        assertTrue("unlabeled_actions" in result.reasons)
    }

    private fun metrics(
        readable: Int,
        actionable: Int,
        labeledActions: Int,
        largeSurfaces: Int = 0,
        truncated: Boolean = false,
    ) = AccessibilityObservationMetrics(
        visibleNodeCount = maxOf(readable, actionable, 1),
        readableNodeCount = readable,
        actionableNodeCount = actionable,
        labeledActionableNodeCount = labeledActions,
        unlabeledLargeSurfaceCount = largeSurfaces,
        truncated = truncated,
    )
}
