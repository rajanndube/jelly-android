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
 * Returns a debug inspector via reflection if `CompositionInspectorDebug` is on
 * the classpath (debug builds only), otherwise the no-op. Reflection lets the
 * release variant avoid any reference to ui-tooling-data which is deliberately
 * unstable across Compose UI versions.
 */
fun resolveCompositionInspector(): CompositionInspector {
    return runCatching {
        val cls = Class.forName("dev.agentation.capture.CompositionInspectorDebug")
        cls.getDeclaredConstructor().newInstance() as CompositionInspector
    }.getOrDefault(NoopCompositionInspector)
}
