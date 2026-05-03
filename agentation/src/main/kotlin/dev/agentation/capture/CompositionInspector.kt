package dev.agentation.capture

import android.view.View
import androidx.compose.ui.geometry.Offset

/**
 * Optional source-location lookup for the Composable under a long-press point.
 *
 * The release implementation returns null; the debug implementation uses
 * androidx.compose.ui:ui-tooling-data to walk the slot tree and find the
 * Composable function name + source file:line.
 *
 * Mirrors the role of `getSourceLocation()` in the web version
 * (package/src/utils/source-location.ts:673-721) which reads React fiber
 * `_debugSource` in development builds.
 */
interface CompositionInspector {
    fun lookup(rootView: View, pointInWindow: Offset): SourceInfo?

    data class SourceInfo(
        val composableName: String?,
        val sourceFile: String?,
    )
}

/** No-op inspector used when ui-tooling-data is unavailable or in release builds. */
internal object NoopCompositionInspector : CompositionInspector {
    override fun lookup(rootView: View, pointInWindow: Offset): CompositionInspector.SourceInfo? = null
}

/**
 * Returns a debug inspector via reflection if `ui-tooling-data` is on the
 * classpath, otherwise the no-op. Reflection lets us avoid a hard compile-time
 * dependency on a deliberately-unstable artifact.
 *
 * Wire-up for v0.4. For now always returns the no-op.
 */
fun resolveCompositionInspector(): CompositionInspector = NoopCompositionInspector
