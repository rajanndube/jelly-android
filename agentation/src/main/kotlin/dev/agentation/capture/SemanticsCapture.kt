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
 * Walks the semantics tree starting from the Compose root's SemanticsOwner,
 * picking the deepest node whose `boundsInWindow` contains the long-press point.
 *
 * Implementation note: `RootForTest` is the public-but-opt-in surface that gives
 * us access to `semanticsOwner` from a plain `View`. AndroidComposeView is
 * internal so we can't cast directly; RootForTest is the documented escape hatch
 * (it's how Compose's own UI test infrastructure walks the semantics tree).
 */
object SemanticsCapture {

    fun capture(
        rootView: View,
        pointInWindow: Offset,
        compositionInspector: CompositionInspector? = null,
    ): CapturedElement? {
        val owner = findSemanticsOwner(rootView) ?: return null

        val rootNode = try {
            owner.rootSemanticsNode
        } catch (t: Throwable) {
            return null
        }

        val hit = findDeepestHit(rootNode, pointInWindow) ?: return null
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

        val displayName = buildDisplayName(role, text, contentDescription, testTag)

        val sourceInfo = compositionInspector?.lookup(rootView, pointInWindow)

        return CapturedElement(
            displayName = displayName,
            elementPath = parentChain,
            role = role,
            contentDescription = contentDescription,
            text = text,
            testTag = testTag,
            stateDescription = stateDescription,
            boundsInWindow = hit.boundsInWindow,
            nearbyElements = collectNearby(hit),
            composableName = sourceInfo?.composableName,
            sourceFile = sourceInfo?.sourceFile,
        )
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
        if (!node.boundsInWindow.contains(point)) return null
        for (child in node.children.asReversed()) {
            val hit = findDeepestHit(child, point)
            if (hit != null) return hit
        }
        return node
    }

    private fun buildParentChain(node: SemanticsNode): String {
        val segments = mutableListOf<String>()
        var current: SemanticsNode? = node
        while (current != null) {
            segments += segmentName(current)
            current = current.parent
        }
        return segments.asReversed().joinToString(" > ")
    }

    private fun segmentName(node: SemanticsNode): String {
        val role = node.config.getOrNull(SemanticsProperties.Role)?.describe()
        val testTag = node.config.getOrNull(SemanticsProperties.TestTag)
        val text = node.config.getOrNull(SemanticsProperties.Text)
            ?.firstOrNull()
            ?.toReadable()
            ?.take(24)
        return when {
            role != null && text != null -> "$role[\"$text\"]"
            role != null && testTag != null -> "$role#$testTag"
            role != null -> role
            testTag != null -> "Node#$testTag"
            text != null -> "Text[\"$text\"]"
            else -> "Node"
        }
    }

    private fun collectNearby(node: SemanticsNode): String? {
        val parent = node.parent ?: return null
        val siblings = parent.children.filter { it.id != node.id }.take(4)
        if (siblings.isEmpty()) return null
        return siblings.joinToString(", ") { segmentName(it) }
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
