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
    val boundsInWindow: Rect,

    /** Concise sibling identifiers, parity with `getNearbyElements`. */
    val nearbyElements: String? = null,

    /** Composable function name + source, debug builds with ui-tooling-data only. */
    val composableName: String? = null,
    val sourceFile: String? = null,
)
