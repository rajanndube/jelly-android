package dev.jelly.capture

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
         * Used as a fallback when no closer `Modifier.jellySource()` tag
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
        //   1. Manual `Modifier.jellySource(...)` on the hit node or any
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
     * Window-level capture for the Application-level install
     * (`Jelly.install()`). Walks the activity's **View** tree to find
     * the deepest View at the press point, then:
     *
     *  - If that View is an `AndroidComposeView` (i.e. a `RootForTest`),
     *    drill into its Compose semantics tree and return rich Compose
     *    metadata.
     *  - Otherwise (it's a regular Android View — `TextView`, `Button`,
     *    `ImageView`, custom XML widgets, etc.), build the
     *    [CapturedElement] from View metadata: class name, resource id
     *    name, text, contentDescription, parent chain.
     *
     * This is what makes the install pattern work for **mixed apps** where
     * some screens are Compose, some are XML, and some are both. We treat
     * the View tree as the master because Compose roots are always Views,
     * but Views aren't always Compose. Bounds are always in window coords
     * so the overlay, screenshot, and baked stroke all line up.
     *
     * Skips any subtree rooted at a view tagged with
     * [dev.jelly.JellyOverlayMarker] — that's our own overlay,
     * which would otherwise be hit-tested first (it's the topmost child of
     * the decor view).
     */
    fun captureInWindow(
        decorView: View,
        pointInWindow: Offset,
        compositionInspector: CompositionInspector? = null,
        hostSource: SourceInfo? = null,
    ): CapturedElement? {
        val deepest = findDeepestViewAt(decorView, pointInWindow) ?: return null

        if (deepest is RootForTest) {
            tryComposeHit(
                root = deepest,
                deepestView = deepest,
                pointInWindow = pointInWindow,
                compositionInspector = compositionInspector,
                hostSource = hostSource,
            )?.let { return it }
            // Compose root contained the point but had nothing meaningful
            // there — fall through and return the AndroidComposeView itself
            // as a generic "Compose region" annotation.
        }

        return captureFromView(deepest, hostSource)
    }

    /**
     * Recursively finds the deepest [View] containing [pointInWindow].
     * Iterates children in reverse z-order (later children draw on top).
     * Skips effectively-invisible views and our own overlay subtree.
     *
     * "Effectively invisible" means any one of: `visibility != VISIBLE`,
     * `alpha <= 0.01`, zero-sized layout, clipped/translated off-screen
     * (via `getGlobalVisibleRect`), or — for ViewGroups — being a
     * transparent **empty shell** (no opaque background and no visible
     * children at the press point).
     *
     * The empty-shell rule is critical for full-screen overlays like
     * "5% cashback" promo layouts: when inactive, the overlay container
     * stays in the view tree at `visibility = VISIBLE` with full bounds,
     * but its inner content is hidden. Without this rule, every long-press
     * on the screen would resolve to the empty overlay container instead
     * of the actually-visible widget underneath.
     */
    private fun findDeepestViewAt(view: View, pointInWindow: Offset): View? {
        if (view.tag === dev.jelly.JellyOverlayMarker) return null
        if (!view.isEffectivelyVisible()) return null

        val loc = IntArray(2).also { view.getLocationInWindow(it) }
        val left = loc[0]
        val top = loc[1]
        val right = left + view.width
        val bottom = top + view.height
        if (pointInWindow.x < left || pointInWindow.x >= right) return null
        if (pointInWindow.y < top || pointInWindow.y >= bottom) return null

        // Defense against off-screen-but-laid-out cases: a view clipped
        // entirely by an ancestor (or a clipBounds) doesn't show on screen
        // even though our bounds check above would say it does. The
        // platform's getGlobalVisibleRect intersects with all ancestor
        // clips and the window — exactly the "is this pixel actually
        // reachable by the user's eye" question.
        val gv = android.graphics.Rect()
        if (!view.getGlobalVisibleRect(gv) || gv.isEmpty) return null
        if (!gv.contains(pointInWindow.x.toInt(), pointInWindow.y.toInt())) return null

        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val hit = findDeepestViewAt(view.getChildAt(i), pointInWindow)
                if (hit != null) return hit
            }
            // No child is visible at this point. If this group has nothing
            // of its own to draw (transparent / null background, no custom
            // onDraw), treat it as a transparent shell and decline the hit
            // so the walk falls through to lower-z siblings — the visible
            // home / bottom-nav widgets sitting underneath the overlay.
            if (!view.hasOpaqueOwnContent()) return null
        }
        return view
    }

    /**
     * Whether this view contributes any pixels of its own to the screen,
     * independent of children. Returns true when:
     *
     *  - The background drawable is non-null and visibly opaque, OR
     *  - There's no background at all and `willNotDraw()` is false (i.e. a
     *    custom view that overrides `onDraw`).
     *
     * Note on the no-background condition: Android flips `willNotDraw` to
     * false whenever any background is set, including a fully-transparent
     * `ColorDrawable`. So we can only trust `!willNotDraw()` when there's
     * no background — otherwise a transparent-bg ViewGroup would falsely
     * register as having content and we'd treat it as a non-shell.
     */
    private fun View.hasOpaqueOwnContent(): Boolean {
        val bg = background
        val bgIsOpaque = when {
            bg == null -> false
            bg is android.graphics.drawable.ColorDrawable ->
                android.graphics.Color.alpha(bg.color) > 8
            else -> bg.opacity != android.graphics.PixelFormat.TRANSPARENT
        }
        if (bgIsOpaque) return true
        // Only trust !willNotDraw when there's no background — otherwise
        // a transparent ColorDrawable still flips that flag and we'd
        // claim opaque content where there is none.
        if (bg == null && !willNotDraw()) return true
        return false
    }

    /**
     * Heuristic for "the user can actually see this view right now". Filters
     * out:
     *
     *  - `View.GONE` / `View.INVISIBLE` — explicit visibility off.
     *  - `alpha <= 0.01` — fully transparent (a common backend-flag pattern).
     *  - Zero-sized layouts — collapsed to nothing by the parent.
     *
     * Anything that fails these filters is excluded from hit-tests so a
     * dormant widget can't steal annotations from the visible one beneath it.
     * (Parent transforms cascade automatically: if a parent fails, the walk
     * never recurses into its children, so a child of an alpha-0 parent is
     * implicitly excluded too.)
     */
    private fun View.isEffectivelyVisible(): Boolean {
        if (visibility != View.VISIBLE) return false
        if (alpha <= 0.01f) return false
        if (width <= 0 || height <= 0) return false
        return true
    }

    /**
     * Drills into a Compose root using two parallel strategies and picks
     * the more precise one:
     *
     *  1. **Semantics tree** — gives rich content (text, role,
     *     contentDescription) but only includes nodes with explicit
     *     semantics modifiers. Server-driven UIs and apps that build
     *     widgets out of plain `Box`/`Column` layouts usually have a
     *     **sparse** semantics tree, so the deepest hit ends up being a
     *     scroll container or other coarse parent.
     *  2. **Slot tree** (via `ui-tooling-data`, debug only) — captures
     *     every composable scope regardless of semantics, so it gives
     *     widget-level granularity even for unsemantic UI.
     *
     * If the slot-tree hit's bounds are meaningfully tighter than the
     * semantic hit's (or the semantic hit is missing entirely), we use
     * the slot-tree info to build the [CapturedElement]. The semantic
     * hit's content (text/role) is still kept when present and
     * applicable.
     */
    private fun tryComposeHit(
        root: RootForTest,
        deepestView: View,
        pointInWindow: Offset,
        compositionInspector: CompositionInspector?,
        hostSource: SourceInfo?,
    ): CapturedElement? {
        if (deepestView.windowToken == null) return null
        @OptIn(InternalComposeUiApi::class)
        val owner = runCatching { root.semanticsOwner }.getOrNull() ?: return null

        val rootLoc = IntArray(2).also { deepestView.getLocationInWindow(it) }
        val pointInRoot = Offset(
            pointInWindow.x - rootLoc[0],
            pointInWindow.y - rootLoc[1],
        )
        val rootNode = try {
            owner.unmergedRootSemanticsNode
        } catch (t: Throwable) {
            runCatching { owner.rootSemanticsNode }.getOrNull() ?: return null
        }
        val semanticHit = findDeepestHit(rootNode, pointInRoot)
        // If the only thing semantics matched is the synthetic root, treat
        // it as no-semantic-hit so the slot-tree path can take over.
        val confidentSemanticHit = semanticHit?.takeUnless {
            it.id == rootNode.id && rootNode.children.none { c -> c.boundsInRoot.contains(pointInRoot) }
        }

        val slotHit = compositionInspector?.lookupHit(deepestView, pointInRoot)

        val preferSlot = shouldPreferSlot(confidentSemanticHit, slotHit, deepestView)

        return when {
            preferSlot && slotHit != null -> buildCapturedFromSlot(
                slotHit = slotHit,
                semanticHit = confidentSemanticHit,
                rootLoc = rootLoc,
                hostSource = hostSource,
            )
            confidentSemanticHit != null -> buildCaptured(
                hit = confidentSemanticHit,
                rootView = deepestView,
                rootLoc = rootLoc,
                pointInRoot = pointInRoot,
                compositionInspector = compositionInspector,
                hostSource = hostSource,
            )
            slotHit != null -> buildCapturedFromSlot(
                slotHit = slotHit,
                semanticHit = null,
                rootLoc = rootLoc,
                hostSource = hostSource,
            )
            else -> null
        }
    }

    /**
     * Returns true when the slot-tree hit gives a more specific match than
     * the semantic hit. Two cases:
     *
     *  1. There's no confident semantic hit — the slot tree is the only
     *     option.
     *  2. The semantic hit's bounds are at least 1.6× the slot-tree hit's
     *     bounds. That ratio is a heuristic: when the semantic hit is
     *     considerably broader (e.g. it's a scroll container while the
     *     slot tree pinpoints a specific widget composable), it's worth
     *     swapping. We don't swap on small differences because semantics
     *     usually carries useful content metadata that slot tree can't
     *     give us.
     */
    private fun shouldPreferSlot(
        semanticHit: SemanticsNode?,
        slotHit: CompositionInspector.Hit?,
        deepestView: View,
    ): Boolean {
        if (slotHit == null) return false
        if (semanticHit == null) return true

        val semBounds = semanticHit.boundsInRoot
        val semArea = semBounds.width.coerceAtLeast(0f) * semBounds.height.coerceAtLeast(0f)
        val slotW = (slotHit.box[2] - slotHit.box[0]).coerceAtLeast(0)
        val slotH = (slotHit.box[3] - slotHit.box[1]).coerceAtLeast(0)
        val slotArea = (slotW * slotH).toFloat()

        // Sanity: if either is degenerate, fall back to semantics.
        if (slotArea <= 1f || semArea <= 1f) return false

        // Slot tree found a strictly-smaller composable scope — use it.
        return semArea / slotArea >= 1.6f
    }

    private fun buildCapturedFromSlot(
        slotHit: CompositionInspector.Hit,
        semanticHit: SemanticsNode?,
        rootLoc: IntArray,
        hostSource: SourceInfo?,
    ): CapturedElement {
        val name = slotHit.composableName?.takeIf { it.isNotBlank() } ?: "Composable"
        // Slot-tree boxes are in AndroidComposeView-local coords. Translate
        // to window coords so they line up with the overlay, screenshot,
        // and baked stroke just like semantic bounds do.
        val box = slotHit.box
        val boundsInWindow = Rect(
            (box[0] + rootLoc[0]).toFloat(),
            (box[1] + rootLoc[1]).toFloat(),
            (box[2] + rootLoc[0]).toFloat(),
            (box[3] + rootLoc[1]).toFloat(),
        )

        // If we also got a semantic hit (broader, but contains real
        // content), keep its text/role/contentDescription as a content
        // hint — the slot tree can't tell us what the composable is
        // *displaying*, only what it's *named*.
        val text = semanticHit?.config?.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.toReadable() }
            ?.takeIf { it.isNotBlank() }
        val role = semanticHit?.config?.getOrNull(SemanticsProperties.Role)?.describe()
        val contentDescription = semanticHit?.config?.getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()
        val testTag = semanticHit?.config?.getOrNull(SemanticsProperties.TestTag)

        // Compose-style display name: prefer "Name \"text\"" when we have
        // text content; otherwise just the composable name.
        val displayName = when {
            text != null -> "$name \"${text.take(40)}\""
            contentDescription != null -> "$name \"${contentDescription.take(40)}\""
            else -> name
        }
        // Parent chain from slot tree (named ancestors only).
        val pathSegments = (slotHit.parentChain + name).distinct()
        val elementPath = pathSegments.joinToString(" > ")

        val sourceFile = slotHit.sourceFile ?: hostSource?.formatted()

        return CapturedElement(
            displayName = displayName,
            elementPath = elementPath,
            role = role,
            contentDescription = contentDescription,
            text = text,
            testTag = testTag,
            stateDescription = null,
            bounds = boundsInWindow,
            nearbyElements = null,
            nearbyText = semanticHit?.let { collectNearbyText(it) },
            composableName = slotHit.composableName,
            sourceFile = sourceFile,
        )
    }

    /** Builds a [CapturedElement] from a hit node, with bounds in window coords. */
    private fun buildCaptured(
        hit: SemanticsNode,
        rootView: View,
        rootLoc: IntArray,
        pointInRoot: Offset,
        compositionInspector: CompositionInspector?,
        hostSource: SourceInfo?,
    ): CapturedElement {
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
        val inferredLabel = if (text == null && contentDescription == null)
            firstDescendantLabel(hit) else null
        val displayName = buildDisplayName(role, text ?: inferredLabel, contentDescription, testTag)

        val pluginSource = nearestSourceInfo(hit)
        val inspectorInfo = compositionInspector?.lookup(rootView, pointInRoot)
        val sourceFile = pluginSource?.formatted()
            ?: inspectorInfo?.sourceFile
            ?: hostSource?.formatted()

        // Translate boundsInRoot → boundsInWindow manually. We avoid
        // SemanticsNode.boundsInWindow because it can drift across versions
        // and (more importantly) we already have the root-to-window offset
        // from getLocationInWindow above, so this is exact.
        val br = hit.boundsInRoot
        val boundsInWindow = Rect(
            br.left + rootLoc[0],
            br.top + rootLoc[1],
            br.right + rootLoc[0],
            br.bottom + rootLoc[1],
        )

        return CapturedElement(
            displayName = displayName,
            elementPath = parentChain,
            role = role,
            contentDescription = contentDescription,
            text = text ?: inferredLabel,
            testTag = testTag,
            stateDescription = stateDescription,
            bounds = boundsInWindow,
            nearbyElements = collectNearby(hit),
            nearbyText = collectNearbyText(hit),
            composableName = inspectorInfo?.composableName,
            sourceFile = sourceFile,
        )
    }

    /**
     * Walks the hit node and its ancestors looking for the closest
     * JellySourceKey. We pick the deepest tagged ancestor — that's
     * usually the actual `@Composable` function that contains the element.
     */
    private fun nearestSourceInfo(node: SemanticsNode): SourceInfo? {
        var current: SemanticsNode? = node
        while (current != null) {
            val info = current.config.getOrNull(JellySourceKey)
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
        if (node.isEffectivelyHidden()) return null
        for (child in node.children.asReversed()) {
            val hit = findDeepestHit(child, point)
            if (hit != null) return hit
        }
        return node
    }

    /**
     * Compose counterpart to [View.isEffectivelyVisible]. Semantic nodes for
     * composables that are present in the tree but shouldn't be considered
     * for hit-testing are flagged either with `HideFromAccessibility` (newer
     * Compose UI) or `InvisibleToUser` (older, now deprecated). Both
     * typically catch `Modifier.alpha(0f)` chains and explicit
     * `Modifier.semantics { invisibleToUser() }` annotations — same
     * backend-flag pattern as the View case, just on the Compose side.
     *
     * Both property keys are looked up via `runCatching` so a Compose UI
     * version that doesn't expose either still leaves us with a usable
     * fallback (zero-bounds check + the View-tree filter that already ran
     * before we reached here).
     */
    private fun SemanticsNode.isEffectivelyHidden(): Boolean {
        // Zero-sized bounds — the composable was placed but laid out to
        // nothing. We can't hit it usefully even if it nominally contains
        // the point.
        val b = boundsInRoot
        if (b.width <= 0f || b.height <= 0f) return true

        val hideFromA11y = runCatching {
            config.contains(SemanticsProperties.HideFromAccessibility)
        }.getOrDefault(false)
        if (hideFromA11y) return true

        @Suppress("DEPRECATION")
        val invisibleToUser = runCatching {
            @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
            config.contains(SemanticsProperties.InvisibleToUser)
        }.getOrDefault(false)
        if (invisibleToUser) return true

        return false
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

    // ─── View-based fallback (XML / mixed-host UIs) ─────────────────────

    /**
     * Builds a [CapturedElement] from an Android [View] when the deepest
     * View at the press point isn't a Compose root. Used by
     * [captureInWindow] for XML-based screens, custom widgets, fragments,
     * or any view-based UI in a mixed app.
     *
     * Extracts: class name, resource id name (`@id/submit_button` →
     * `submit_button`), TextView text, contentDescription, tag, and a
     * parent-class chain that mirrors the Compose `elementPath`.
     */
    private fun captureFromView(view: View, hostSource: SourceInfo?): CapturedElement {
        val loc = IntArray(2).also { view.getLocationInWindow(it) }
        val bounds = Rect(
            loc[0].toFloat(),
            loc[1].toFloat(),
            (loc[0] + view.width).toFloat(),
            (loc[1] + view.height).toFloat(),
        )
        val className = view.javaClass.simpleName.ifEmpty { "View" }
        val idName = viewIdName(view)
        val text = (view as? android.widget.TextView)
            ?.text?.toString()?.takeIf { it.isNotBlank() }
        val contentDescription = view.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        val displayName = buildViewDisplayName(className, idName, text, contentDescription)
        val elementPath = buildViewPath(view)

        return CapturedElement(
            displayName = displayName,
            elementPath = elementPath,
            role = className,
            contentDescription = contentDescription,
            text = text,
            testTag = idName,
            stateDescription = null,
            bounds = bounds,
            nearbyElements = collectNearbyViewIds(view),
            nearbyText = collectNearbyViewText(view),
            composableName = null,
            sourceFile = hostSource?.formatted(),
        )
    }

    private fun viewIdName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
    }

    private fun buildViewDisplayName(
        className: String,
        idName: String?,
        text: String?,
        contentDescription: String?,
    ): String {
        val label = text ?: contentDescription
        return when {
            label != null -> "$className \"${label.take(40)}\""
            idName != null -> "$className #$idName"
            else -> className
        }
    }

    private fun viewSegmentName(view: View): String {
        val cls = view.javaClass.simpleName.ifEmpty { "View" }
        val idName = viewIdName(view)
        val text = (view as? android.widget.TextView)
            ?.text?.toString()?.trim()?.take(24)?.takeIf { it.isNotBlank() }
        val cd = view.contentDescription?.toString()?.trim()?.take(24)?.takeIf { it.isNotBlank() }
        return when {
            text != null -> "$cls[\"$text\"]"
            cd != null -> "$cls[\"$cd\"]"
            idName != null -> "$cls#$idName"
            else -> cls
        }
    }

    /**
     * Walks ancestors building a "Outer > Inner > Leaf" path. Collapses
     * runs of unlabelled generic ViewGroups (FrameLayout, LinearLayout,
     * etc. with no id/text/cd) into an ellipsis so the meaningful
     * segments stand out — same convention as the Compose [buildParentChain].
     */
    private fun buildViewPath(view: View): String {
        val segments = mutableListOf<String>()
        var current: View? = view
        while (current != null) {
            segments += viewSegmentName(current)
            current = current.parent as? View
        }
        val ordered = segments.asReversed()
        val collapsed = mutableListOf<String>()
        var skipping = false
        val genericNames = setOf(
            "FrameLayout", "LinearLayout", "RelativeLayout", "ConstraintLayout",
            "ViewGroup", "View", "ContentFrameLayout", "FitWindowsFrameLayout",
            "ContentFrameLayout", "ActionBarOverlayLayout", "FragmentContainerView",
        )
        for (seg in ordered) {
            if (seg in genericNames) {
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

    private fun collectNearbyViewIds(view: View): String? {
        val parent = view.parent as? ViewGroup ?: return null
        val labels = mutableListOf<String>()
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChildAt(i)
            if (sibling === view) continue
            labels += viewSegmentName(sibling)
            if (labels.size >= 4) break
        }
        return labels.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun collectNearbyViewText(view: View): String? {
        val parent = view.parent as? ViewGroup ?: return null
        val labels = mutableListOf<String>()
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChildAt(i)
            if (sibling === view) continue
            val text = (sibling as? android.widget.TextView)
                ?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: sibling.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: descendantTextOf(sibling)
            if (text != null) labels += text.take(40)
            if (labels.size >= 6) break
        }
        return labels.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun descendantTextOf(view: View, depth: Int = 0): String? {
        if (depth > 4) return null
        if (view is android.widget.TextView) {
            val t = view.text?.toString()?.trim()
            if (!t.isNullOrBlank()) return t
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val nested = descendantTextOf(view.getChildAt(i), depth + 1)
                if (nested != null) return nested
            }
        }
        return null
    }
}
