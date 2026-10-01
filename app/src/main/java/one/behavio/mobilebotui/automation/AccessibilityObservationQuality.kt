package one.behavio.mobilebotui.automation

internal data class AccessibilityObservationMetrics(
    val visibleNodeCount: Int,
    val readableNodeCount: Int,
    val actionableNodeCount: Int,
    val labeledActionableNodeCount: Int,
    val unlabeledLargeSurfaceCount: Int,
    val truncated: Boolean,
)

internal data class AccessibilityObservationAssessment(
    val quality: String,
    val supportRecommended: Boolean,
    val recommendedSupport: String,
    val reasons: List<String>,
)

internal fun assessAccessibilityObservation(
    metrics: AccessibilityObservationMetrics,
): AccessibilityObservationAssessment {
    if (metrics.readableNodeCount == 0) {
        return AccessibilityObservationAssessment(
            quality = "insufficient",
            supportRecommended = true,
            recommendedSupport = "ocr_then_vision",
            reasons = buildList {
                add("no_semantic_content")
                if (metrics.unlabeledLargeSurfaceCount > 0) add("unlabeled_large_surface")
                if (metrics.actionableNodeCount > 0) add("unlabeled_actions")
            },
        )
    }

    if (metrics.unlabeledLargeSurfaceCount > 0) {
        return AccessibilityObservationAssessment(
            quality = "partial",
            supportRecommended = true,
            recommendedSupport = "ocr_then_vision",
            reasons = listOf("unlabeled_large_surface"),
        )
    }

    if (metrics.truncated) {
        return AccessibilityObservationAssessment(
            quality = "partial",
            supportRecommended = false,
            recommendedSupport = "accessibility_scroll_or_targeted_read",
            reasons = listOf("accessibility_tree_truncated"),
        )
    }

    return AccessibilityObservationAssessment(
        quality = "sufficient",
        supportRecommended = false,
        recommendedSupport = "none",
        reasons = emptyList(),
    )
}
