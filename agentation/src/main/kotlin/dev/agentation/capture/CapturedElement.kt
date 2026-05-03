package dev.agentation.capture

import androidx.compose.ui.geometry.Rect

/**
 * Snapshot of metadata extracted from a SemanticsNode at a long-press point.
 *
 * This is the Android equivalent of the data the web `identifyElement()` returns
 * (package/src/utils/element-identification.ts:103-216) plus optional source
 * info from compose-ui-tooling-data.
 */
data class CapturedElement(
    /** Human-readable name shown in the popup header. e.g. `Button "Save"`. */
    val displayName: String,

    /** Parent chain readable path. e.g. `Column > Row > Button`. */
    val elementPath: String,

    val role: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    val testTag: String? = null,
    val stateDescription: String? = null,
    /**
     * Element bounds in the AndroidComposeView's *root* coordinate space —
     * i.e., the same space pointer events arrive in and the same space the
     * captured bitmap uses. This makes the live preview, the hit-test, and the
     * baked stroke rectangle all align without per-call coord conversion.
     */
    val bounds: Rect,

    /** Concise sibling identifiers, parity with `getNearbyElements`. */
    val nearbyElements: String? = null,

    /** Readable text content of sibling elements, parity with `getNearbyText`. */
    val nearbyText: String? = null,

    /** Composable function name + source, debug builds with ui-tooling-data only. */
    val composableName: String? = null,
    val sourceFile: String? = null,
)
