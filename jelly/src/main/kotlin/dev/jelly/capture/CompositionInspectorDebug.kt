package dev.jelly.capture

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.geometry.Offset

/**
 * Best-effort [CompositionInspector] that resolves composable name + source
 * `file:line` for the View under a long-press point.
 *
 * Loaded reflectively from [resolveCompositionInspector]. The class itself
 * has no compile-time dependency on `compose-ui-tooling-data` — every entry
 * point into that library is reached via [Class.forName] and wrapped in
 * `runCatching`, so the SDK works equally on consumers who ship
 * `ui-tooling-data` (typically `debugImplementation`) and consumers who
 * don't. When the API is missing or version-skewed, we return null and the
 * caller falls back to the activity stack-walker source attribution.
 *
 * Strategy:
 *   1. Find the AbstractComposeView in the View hierarchy.
 *   2. Reflect into its `compositionContext`/`composer` to obtain a
 *      `CompositionData`.
 *   3. Call `CompositionData.asTree()` from `androidx.compose.ui.tooling.data`
 *      to get a `Group` tree.
 *   4. Walk the tree depth-first, picking the deepest Group whose `box`
 *      contains the touch point.
 *   5. Read `Group.name` and `Group.location.sourceFile` + `lineNumber`.
 *
 * Mirrors the role of `getSourceLocation()` in package/src/utils/source-location.ts:673-721
 * which uses React fiber `_debugSource` for the same purpose.
 */
internal class CompositionInspectorDebug : CompositionInspector {

    override fun lookup(rootView: View, pointInWindow: Offset): CompositionInspector.SourceInfo? {
        // The interface name is "pointInWindow" but historically the caller
        // (SemanticsCapture) passes the point in the AndroidComposeView's
        // local coords — which is what slot-tree boxes use. Keeping that
        // convention so existing source-location lookups still work.
        return runCatching {
            val composeView = findAbstractComposeView(rootView) ?: return null
            val compositionData = readCompositionData(composeView) ?: return null
            val rootGroup = invokeAsTree(compositionData) ?: return null
            val hit = findDeepestHit(rootGroup, pointInWindow.x.toInt(), pointInWindow.y.toInt())
                ?: return null
            extractSourceInfo(hit)
        }.getOrNull()
    }

    override fun lookupHit(rootView: View, pointInWindow: Offset): CompositionInspector.Hit? {
        return runCatching {
            val composeView = findAbstractComposeView(rootView) ?: return null
            val compositionData = readCompositionData(composeView) ?: return null
            val rootGroup = invokeAsTree(compositionData) ?: return null

            // Caller passes the press point in AndroidComposeView-local
            // coords — same convention as [lookup]. Slot-tree boxes are
            // already in that space, so no translation needed here.
            val px = pointInWindow.x.toInt()
            val py = pointInWindow.y.toInt()

            // Capture the chain of ancestors as we walk down so we can
            // return a meaningful "Outer > Inner > Leaf" parent path.
            val ancestors = mutableListOf<Any>()
            val hit = findDeepestHitWithAncestors(rootGroup, px, py, ancestors)
                ?: return null

            val box = readBox(hit) ?: return null
            val info = extractSourceInfo(hit)
            // If the deepest hit is anonymous, walk up to the closest named
            // ancestor — that's typically the @Composable function the user
            // pressed inside. Avoids reporting "<anonymous>" as the name.
            val (composableName, sourceFile) = if (info?.composableName.isNullOrBlank()) {
                ancestors.asReversed().firstNotNullOfOrNull { ancestor ->
                    extractSourceInfo(ancestor)?.takeIf { !it.composableName.isNullOrBlank() }
                }?.let { it.composableName to it.sourceFile } ?: (info?.composableName to info?.sourceFile)
            } else {
                info!!.composableName to info.sourceFile
            }

            val parentChain = ancestors
                .mapNotNull { extractSourceInfo(it)?.composableName?.takeIf { n -> n.isNotBlank() } }

            CompositionInspector.Hit(
                composableName = composableName,
                sourceFile = sourceFile,
                box = box,
                parentChain = parentChain,
            )
        }.getOrNull()
    }

    private fun findDeepestHitWithAncestors(
        group: Any,
        px: Int,
        py: Int,
        ancestors: MutableList<Any>,
    ): Any? {
        val box = readBox(group) ?: return null
        if (px < box[0] || py < box[1] || px >= box[2] || py >= box[3]) return null
        ancestors += group
        val children = readChildren(group) ?: emptyList()
        for (child in children.reversed()) {
            val hit = findDeepestHitWithAncestors(child, px, py, ancestors)
            if (hit != null) return hit
        }
        // Children didn't match, but we did — drop ourselves from the
        // ancestor list so the caller sees only ancestors strictly above
        // the hit.
        ancestors.removeAt(ancestors.size - 1)
        return group
    }

    private fun findAbstractComposeView(view: View): View? {
        if (view.javaClass.name.startsWith("androidx.compose.ui.platform.AbstractComposeView") ||
            view.javaClass.simpleName.endsWith("ComposeView")
        ) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findAbstractComposeView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun readCompositionData(view: View): Any? {
        // Try the documented entry point first: AbstractComposeView holds a
        // CompositionContext from which a CompositionData can sometimes be
        // obtained. Fall back through known reflective paths.
        val candidates = listOf(
            "getCompositionContext",
            "getComposition",
            "compositionContext\$ui_release",
        )
        for (name in candidates) {
            val data = runCatching {
                val method = view.javaClass.methods.firstOrNull { it.name == name } ?: return@runCatching null
                method.invoke(view)
            }.getOrNull() ?: continue
            // CompositionContext doesn't directly expose CompositionData; some
            // versions expose `compositionData` via reflection.
            val cd = runCatching {
                data.javaClass.methods.firstOrNull { it.name == "getCompositionData" }?.invoke(data)
            }.getOrNull()
            if (cd != null) return cd
            if (data.javaClass.name.contains("CompositionData")) return data
        }
        return null
    }

    private fun invokeAsTree(compositionData: Any): Any? {
        // androidx.compose.ui.tooling.data.SlotTreeKt has an extension
        // CompositionData.asTree(): Group. Its presence depends on whether
        // ui-tooling-data is on the classpath.
        return runCatching {
            val cls = Class.forName("androidx.compose.ui.tooling.data.SlotTreeKt")
            val method = cls.methods.firstOrNull { it.name == "asTree" && it.parameterCount == 1 }
                ?: return null
            method.invoke(null, compositionData)
        }.getOrNull()
    }

    private fun findDeepestHit(group: Any, px: Int, py: Int): Any? {
        val box = readBox(group) ?: return null
        if (px < box[0] || py < box[1] || px >= box[2] || py >= box[3]) return null
        val children = readChildren(group) ?: emptyList()
        for (child in children.reversed()) {
            val hit = findDeepestHit(child, px, py)
            if (hit != null) return hit
        }
        return group
    }

    private fun readBox(group: Any): IntArray? = runCatching {
        val box = group.javaClass.methods.firstOrNull { it.name == "getBox" }?.invoke(group)
            ?: return null
        val left = box.intProp("getLeft")
        val top = box.intProp("getTop")
        val right = box.intProp("getRight")
        val bottom = box.intProp("getBottom")
        intArrayOf(left, top, right, bottom)
    }.getOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun readChildren(group: Any): Collection<Any>? = runCatching {
        group.javaClass.methods.firstOrNull { it.name == "getChildren" }?.invoke(group) as? Collection<Any>
    }.getOrNull()

    private fun extractSourceInfo(group: Any): CompositionInspector.SourceInfo? = runCatching {
        val name = group.javaClass.methods.firstOrNull { it.name == "getName" }
            ?.invoke(group) as? String
        val location = group.javaClass.methods.firstOrNull { it.name == "getLocation" }
            ?.invoke(group)
        val sourceFile = location?.javaClass?.methods?.firstOrNull { it.name == "getSourceFile" }
            ?.invoke(location) as? String
        val lineNumber = location?.javaClass?.methods?.firstOrNull { it.name == "getLineNumber" }
            ?.invoke(location) as? Int
        if (name == null && sourceFile == null) return null
        CompositionInspector.SourceInfo(
            composableName = name,
            sourceFile = if (sourceFile != null && lineNumber != null && lineNumber > 0)
                "$sourceFile:$lineNumber" else sourceFile,
        )
    }.getOrNull()

    private fun Any.intProp(getter: String): Int =
        (javaClass.methods.firstOrNull { it.name == getter }?.invoke(this) as? Int) ?: 0
}
