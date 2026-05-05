package dev.jelly.capture

import android.view.View
import androidx.compose.ui.geometry.Offset

/**
 * Optional source-location lookup for the Composable under a long-press point.
 *
 * The shipping implementation ([CompositionInspectorDebug]) walks the slot
 * tree from `androidx.compose.ui:ui-tooling-data` to find the composable
 * function name + source `file:line`. All access is reflective so the SDK
 * works whether or not the consumer ships `ui-tooling-data` on the runtime
 * classpath; when it isn't present, [resolveCompositionInspector] returns
 * a no-op and the SDK falls back to activity stack-walker source
 * attribution.
 */
interface CompositionInspector {
    fun lookup(rootView: View, pointInWindow: Offset): SourceInfo?

    /**
     * Richer slot-tree lookup that also returns the **layout** bounds and a
     * named ancestor chain. Used as a fallback when the Compose semantics
     * tree is too sparse to give a precise hit — server-driven UIs and
     * apps that don't add semantics modifiers to custom composables tend
     * to fall into this category. The slot tree captures every composable
     * scope regardless of semantics, so it can pinpoint a widget that
     * would otherwise resolve to "the whole scroll container".
     */
    fun lookupHit(rootView: View, pointInWindow: Offset): Hit? = null

    data class SourceInfo(
        val composableName: String?,
        val sourceFile: String?,
    )

    /**
     * Slot-tree hit. [box] is in the Compose root view's local pixel
     * coordinates (translate by [View.getLocationInWindow] to get window
     * coords). [composableName] is the deepest non-anonymous ancestor's
     * name. [parentChain] is a leaf-last list of named ancestors (without
     * line numbers), suitable for showing as `Outer > Inner > Leaf`.
     */
    data class Hit(
        val composableName: String?,
        val sourceFile: String?,
        val box: IntArray,
        val parentChain: List<String>,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Hit) return false
            return composableName == other.composableName &&
                sourceFile == other.sourceFile &&
                box.contentEquals(other.box) &&
                parentChain == other.parentChain
        }

        override fun hashCode(): Int {
            var result = composableName?.hashCode() ?: 0
            result = 31 * result + (sourceFile?.hashCode() ?: 0)
            result = 31 * result + box.contentHashCode()
            result = 31 * result + parentChain.hashCode()
            return result
        }
    }
}

/** No-op inspector used when ui-tooling-data is unavailable or in release builds. */
internal object NoopCompositionInspector : CompositionInspector {
    override fun lookup(rootView: View, pointInWindow: Offset): CompositionInspector.SourceInfo? = null
    override fun lookupHit(rootView: View, pointInWindow: Offset): CompositionInspector.Hit? = null
}

/**
 * Returns a debug inspector via reflection if `CompositionInspectorDebug` is on
 * the classpath (debug builds only), otherwise the no-op. Reflection lets the
 * release variant avoid any reference to ui-tooling-data which is deliberately
 * unstable across Compose UI versions.
 */
fun resolveCompositionInspector(): CompositionInspector {
    return runCatching {
        val cls = Class.forName("dev.jelly.capture.CompositionInspectorDebug")
        cls.getDeclaredConstructor().newInstance() as CompositionInspector
    }.getOrDefault(NoopCompositionInspector)
}
