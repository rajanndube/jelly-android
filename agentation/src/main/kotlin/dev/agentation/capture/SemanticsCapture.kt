package dev.agentation.capture

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString

/**
 * Hit-test against the live Compose semantics tree.
 *
 * Mirrors `document.elementFromPoint()` + `identifyElement()` in the web version
 * (package/src/components/page-toolbar-css/index.tsx:238-250 and
 * package/src/utils/element-identification.ts:103-216).
 *
 * Most layout containers in Compose (Box, Column, Surface, Card) carry no
 * semantic properties of their own, so we can't rely on the node's own role/text
 * for naming. Instead we fall back to scanning descendants for the first text
 * label and use it as an inferred name — that's how we turn "Node > Node > Node"
 * into "Surface[\"Login form\"] > Card > Button[\"Submit\"]".
 */
object SemanticsCapture {

    fun capture(
        rootView: View,
        /**
         * The press point in the AndroidComposeView's *root* coordinate space
         * — what `pointerInput` provides directly. Naming it pointInRoot keeps
         * us honest about which space we're in (the previous `pointInWindow`
         * was a misnomer that caused live-preview offset bugs).
         */
        pointInRoot: Offset,
        compositionInspector: CompositionInspector? = null,
        /**
         * Activity-level source captured by the host SDK at first composition.
         * Used as a fallback when no closer `Modifier.agentationSource()` tag
         * is found in the semantics tree. See `detectHostSource()`.
         */
        hostSource: SourceInfo? = null,
    ): CapturedElement? {
        val owner = findSemanticsOwner(rootView) ?: return null

        // Use the *unmerged* semantics tree so individual leaf nodes (Text,
        // Icon, etc.) remain addressable. The merged tree collapses children
        // into their nearest ancestor that defines semantics, which makes
        // small elements like a heading effectively un-pickable — pressing on
        // or near them lands on a Surface/Card that fills the screen.
        val rootNode = try {
            owner.unmergedRootSemanticsNode
        } catch (t: Throwable) {
            try {
                owner.rootSemanticsNode
            } catch (t2: Throwable) {
                return null
            }
        }

        val hit = findDeepestHit(rootNode, pointInRoot) ?: return null
        val parentChain = buildParentChain(hit)

        val role = hit.config.getOrNull(SemanticsProperties.Role)?.describe()
        val contentDescription = hit.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()
        val text = hit.config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.toReadable() }
            ?.takeIf { it.isNotBlank() }
            ?: hit.config.getOrNull(SemanticsProperties.EditableText)?.toReadable()
        val testTag = hit.config.getOrNull(SemanticsProperties.TestTag)
        val stateDescription = hit.config.getOrNull(SemanticsProperties.StateDescription)

        // For unlabelled leaves, scan descendants for any visible text.
        val inferredLabel = if (text == null && contentDescription == null)
            firstDescendantLabel(hit) else null

        val displayName = buildDisplayName(role, text ?: inferredLabel, contentDescription, testTag)

        // Source resolution priority:
        //   1. Manual `Modifier.agentationSource(...)` on the hit node or any
        //      ancestor — most precise, dev-controlled.
        //   2. ui-tooling-data inspector — best-effort reflective lookup, may
        //      fail silently across Compose UI versions.
        //   3. Host activity stack-walk — always available, activity-level.
        val pluginSource = nearestSourceInfo(hit)
        val inspectorInfo = compositionInspector?.lookup(rootView, pointInRoot)
        val sourceFile = pluginSource?.formatted()
            ?: inspectorInfo?.sourceFile
            ?: hostSource?.formatted()

        return CapturedElement(
            displayName = displayName,
            elementPath = parentChain,
            role = role,
            contentDescription = contentDescription,
            text = text ?: inferredLabel,
            testTag = testTag,
            stateDescription = stateDescription,
            bounds = hit.boundsInRoot,
            nearbyElements = collectNearby(hit),
            nearbyText = collectNearbyText(hit),
            composableName = inspectorInfo?.composableName,
            sourceFile = sourceFile,
        )
    }

    /**
     * Walks the hit node and its ancestors looking for the closest
     * AgentationSourceKey. We pick the deepest tagged ancestor — that's
     * usually the actual `@Composable` function that contains the element.
     */
    private fun nearestSourceInfo(node: SemanticsNode): SourceInfo? {
        var current: SemanticsNode? = node
        while (current != null) {
            val info = current.config.getOrNull(AgentationSourceKey)
            if (info != null) return info
            current = current.parent
        }
        return null
    }

    @OptIn(InternalComposeUiApi::class)
    private fun findSemanticsOwner(view: View): SemanticsOwner? {
        if (view is RootForTest) return view.semanticsOwner
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findSemanticsOwner(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun findDeepestHit(node: SemanticsNode, point: Offset): SemanticsNode? {
        if (!node.boundsInRoot.contains(point)) return null
        for (child in node.children.asReversed()) {
            val hit = findDeepestHit(child, point)
            if (hit != null) return hit
        }
        return node
    }

    /**
     * Builds a readable parent chain. Skips the synthetic root node and
     * collapses runs of generic "Node" entries into a single ellipsis so the
     * meaningful labels stand out.
     */
    private fun buildParentChain(node: SemanticsNode): String {
        val segments = mutableListOf<String>()
        var current: SemanticsNode? = node
        while (current != null) {
            // Skip the synthetic root (it has no parent and no real content).
            if (current.parent == null && segments.isNotEmpty()) break
            segments += segmentName(current)
            current = current.parent
        }
        // segments is leaf-first; reverse for root-first display.
        val ordered = segments.asReversed()
        // Collapse runs of "Node" entries.
        val collapsed = mutableListOf<String>()
        var skipping = false
        for (seg in ordered) {
            if (seg == "Node") {
                if (!skipping) {
                    collapsed += "…"
                    skipping = true
                }
            } else {
                skipping = false
                collapsed += seg
            }
        }
        return collapsed.joinToString(" > ")
    }

    private fun segmentName(node: SemanticsNode): String {
        val role = node.config.getOrNull(SemanticsProperties.Role)?.describe()
        val testTag = node.config.getOrNull(SemanticsProperties.TestTag)
        val ownText = node.config.getOrNull(SemanticsProperties.Text)
            ?.firstOrNull()
            ?.toReadable()
            ?.takeIf { it.isNotBlank() }
            ?.take(24)
        val ownDesc = node.config.getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.take(24)
        // If the node itself carries no obvious label, fall back to its first
        // descendant's text — turns "Node" into something like
        // "containing 'Login form'".
        val inferred = if (ownText == null && ownDesc == null)
            firstDescendantLabel(node)?.take(24) else null

        val label = ownText ?: ownDesc ?: inferred
        return when {
            role != null && label != null -> "$role[\"$label\"]"
            role != null && testTag != null -> "$role#$testTag"
            role != null -> role
            testTag != null -> "Node#$testTag"
            label != null && inferred != null -> "containing[\"$inferred\"]"
            label != null -> "Text[\"$label\"]"
            else -> "Node"
        }
    }

    /**
     * Walks descendants depth-first, returning the first non-blank text or
     * contentDescription it finds. Used to label otherwise-anonymous layout
     * containers.
     */
    private fun firstDescendantLabel(node: SemanticsNode, depth: Int = 0): String? {
        if (depth > 6) return null
        for (child in node.children) {
            val text = child.config.getOrNull(SemanticsProperties.Text)
                ?.firstOrNull()
                ?.toReadable()
                ?.takeIf { it.isNotBlank() }
            if (text != null) return text
            val desc = child.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
            if (desc != null) return desc
            val nested = firstDescendantLabel(child, depth + 1)
            if (nested != null) return nested
        }
        return null
    }

    private fun collectNearby(node: SemanticsNode): String? {
        val parent = node.parent ?: return null
        val siblings = parent.children.filter { it.id != node.id }.take(4)
        if (siblings.isEmpty()) return null
        return siblings.joinToString(", ") { segmentName(it) }
    }

    /**
     * Collects readable text labels from sibling nodes (and their descendants
     * if siblings are layout-only). Equivalent to the web `getNearbyText` —
     * gives an AI agent context like "Login form, Email, Password" so it can
     * understand what surrounds the selected element.
     */
    private fun collectNearbyText(node: SemanticsNode): String? {
        val parent = node.parent ?: return null
        val labels = mutableListOf<String>()
        for (sibling in parent.children) {
            if (sibling.id == node.id) continue
            val ownText = sibling.config.getOrNull(SemanticsProperties.Text)
                ?.firstOrNull()?.toReadable()?.takeIf { it.isNotBlank() }
            val ownDesc = sibling.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.firstOrNull()?.takeIf { it.isNotBlank() }
            val label = ownText ?: ownDesc ?: firstDescendantLabel(sibling)
            if (!label.isNullOrBlank()) labels += label.trim().take(40)
            if (labels.size >= 6) break
        }
        return labels.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun buildDisplayName(
        role: String?,
        text: String?,
        contentDescription: String?,
        testTag: String?,
    ): String {
        val label = text?.takeIf { it.isNotBlank() }
            ?: contentDescription?.takeIf { it.isNotBlank() }
        val baseRole = role ?: "Composable"
        return when {
            label != null -> "$baseRole \"${label.take(40)}\""
            testTag != null -> "$baseRole #$testTag"
            else -> baseRole
        }
    }

    private fun Role.describe(): String = toString()

    private fun AnnotatedString.toReadable(): String = text
}
