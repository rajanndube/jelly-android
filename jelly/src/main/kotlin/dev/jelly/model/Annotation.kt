package dev.jelly.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the `Annotation` type in package/src/types.ts:5-69.
 *
 * Field names use the same wire format as the web version so the same MCP
 * /sessions endpoint and downstream agents work for both clients.
 */
@Serializable
data class Annotation(
    val id: String,
    val x: Float,
    val y: Float,
    val comment: String,
    val element: String,
    val elementPath: String,
    val timestamp: Long,
    val selectedText: String? = null,
    val boundingBox: BoundingBox? = null,
    val nearbyText: String? = null,
    val cssClasses: String? = null,
    val nearbyElements: String? = null,
    val computedStyles: String? = null,
    val fullPath: String? = null,
    val accessibility: String? = null,
    val isMultiSelect: Boolean? = null,
    val isFixed: Boolean? = null,
    @SerialName("reactComponents") val composableHierarchy: String? = null,
    val sourceFile: String? = null,
    val drawingIndex: Int? = null,
    val elementBoundingBoxes: List<BoundingBox>? = null,
    val kind: AnnotationKind? = null,
    val placement: PlacementData? = null,
    val rearrange: RearrangeData? = null,

    // Mobile-only: local file path to a region screenshot, if captured.
    val screenshotPath: String? = null,

    // Protocol fields (set when syncing to server).
    val sessionId: String? = null,
    val url: String? = null,
    val intent: AnnotationIntent? = null,
    val severity: AnnotationSeverity? = null,
    val status: AnnotationStatus? = null,
    val thread: List<ThreadMessage>? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val resolvedAt: String? = null,
    val resolvedBy: ResolvedBy? = null,
    val authorId: String? = null,

    @SerialName("_syncedTo") val syncedTo: String? = null,
)

@Serializable
data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

@Serializable
enum class AnnotationKind {
    @SerialName("feedback") Feedback,
    @SerialName("placement") Placement,
    @SerialName("rearrange") Rearrange,
}

@Serializable
enum class AnnotationIntent {
    @SerialName("fix") Fix,
    @SerialName("change") Change,
    @SerialName("question") Question,
    @SerialName("approve") Approve,
}

@Serializable
enum class AnnotationSeverity {
    @SerialName("blocking") Blocking,
    @SerialName("important") Important,
    @SerialName("suggestion") Suggestion,
}

@Serializable
enum class AnnotationStatus {
    @SerialName("pending") Pending,
    @SerialName("acknowledged") Acknowledged,
    @SerialName("resolved") Resolved,
    @SerialName("dismissed") Dismissed,
}

@Serializable
enum class ResolvedBy {
    @SerialName("human") Human,
    @SerialName("agent") Agent,
}

@Serializable
data class PlacementData(
    val componentType: String,
    val width: Float,
    val height: Float,
    val scrollY: Float,
    val text: String? = null,
)

@Serializable
data class RearrangeData(
    val selector: String,
    val label: String,
    val tagName: String,
    val originalRect: BoundingBox,
    val currentRect: BoundingBox,
)
