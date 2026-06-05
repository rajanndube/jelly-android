package dev.jelly

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.jelly.capture.BakedImage
import dev.jelly.capture.CapturedElement
import dev.jelly.capture.Screenshot
import dev.jelly.capture.SemanticsCapture
import dev.jelly.capture.SourceInfo
import dev.jelly.capture.detectHostSource
import dev.jelly.capture.resolveCompositionInspector
import dev.jelly.model.Annotation
import dev.jelly.model.BoundingBox
import dev.jelly.output.OutputGenerator
import dev.jelly.storage.AnnotationStore
import dev.jelly.storage.Settings
import dev.jelly.storage.SettingsStore
import dev.jelly.sync.DeviceInfo
import dev.jelly.sync.JellyApi
import dev.jelly.sync.pushAllAnnotations
import dev.jelly.theme.JellyTheme
import dev.jelly.ui.AnnotationMarker
import dev.jelly.ui.AnnotationPopup
import dev.jelly.ui.AnnotationToolbar
import dev.jelly.ui.AnnotationsScreen
import dev.jelly.ui.CatchUpSyncStatus
import dev.jelly.ui.LiveHoverOverlay
import dev.jelly.ui.PopupSubmission
import dev.jelly.ui.SettingsSheet
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Shared overlay implementation for both integration paths:
 *
 *  - **`Jelly.install(application)`** — attached as a transparent
 *    `ComposeView` to the activity's `decorView`. `mode = OverlayMode.Window`,
 *    so hit-tests run against every Compose root in the decor view and bounds
 *    are window-relative.
 *  - **`@Composable fun Jelly { content }`** (back-compat) — wraps the
 *    host content in a `Box`. `mode = OverlayMode.Embedded`, and the overlay
 *    only sees the single root it's hosted in.
 *
 * Everything else — the toolbar, popup, marker dots, screenshot, baking,
 * sync, storage — is identical between paths. This composable is the single
 * source of truth for that logic.
 */
internal sealed class OverlayMode {
    /**
     * Used by `Jelly.install`. The overlay is attached to the activity
     * decor view, so it can see every Compose root in the window. Captures
     * use `SemanticsCapture.captureInWindow` and `Screenshot.captureWindow`.
     */
    data class Window(val activity: Activity) : OverlayMode()

    /**
     * Used by the back-compat `@Composable fun Jelly`. The overlay only
     * has access to the single AndroidComposeView it lives in. Captures use
     * the legacy `SemanticsCapture.capture` (root-relative coords).
     */
    data class Embedded(
        val rootView: android.view.View,
        val window: android.view.Window?,
    ) : OverlayMode()
}

@Composable
internal fun JellyOverlayContent(
    config: JellyConfig,
    mode: OverlayMode,
    /**
     * Optional shared state. When the install path attaches both a capture
     * overlay (decor view) and a separate toolbar window, both ComposeViews
     * pass the same state instance so taps on the FAB in one are visible
     * to the other. When null, a fresh state is created locally — that's
     * the legacy `@Composable Jelly { }` Embedded path.
     */
    sharedState: JellyOverlayState? = null,
    /**
     * Whether to render the toolbar inline. The install path renders the
     * toolbar in its own [TYPE_APPLICATION_PANEL] window instead, so it
     * passes false; legacy embedded mode renders the toolbar inline so it
     * passes true (the default).
     */
    renderToolbar: Boolean = true,
    onAnnotationAdd: (Annotation) -> Unit = {},
    onAnnotationDelete: (Annotation) -> Unit = {},
    onAnnotationsClear: (List<Annotation>) -> Unit = {},
    onCopy: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { AnnotationStore(context.applicationContext) }
    val settingsStore = remember(context) { SettingsStore(context.applicationContext) }
    val state = sharedState ?: remember { JellyOverlayState() }

    // Activity context used for screenshot, viewport, and screenKey defaults.
    val captureWindow = when (mode) {
        is OverlayMode.Window -> mode.activity.window
        is OverlayMode.Embedded -> mode.window
    }
    val captureView: android.view.View = when (mode) {
        is OverlayMode.Window -> mode.activity.window.decorView
        is OverlayMode.Embedded -> mode.rootView
    }
    val activity: Activity? = when (mode) {
        is OverlayMode.Window -> mode.activity
        is OverlayMode.Embedded -> context.findActivity()
    }
    val screenKey = remember(config.screenKey, activity) {
        config.screenKey ?: (activity?.javaClass?.simpleName ?: "default")
    }
    val annotations by store.observe(screenKey).collectAsState(initial = emptyList())
    val persistedSettings by settingsStore.settings.collectAsState(
        initial = Settings(
            detailLevel = config.detailLevel,
            accentColor = config.accentColor,
            syncEnabled = config.endpoint != null,
            endpoint = config.endpoint,
        ),
    )

    // Initialize activeSessionId from config exactly once for this state.
    LaunchedEffect(state, config.sessionId) {
        if (state.activeSessionId == null) state.activeSessionId = config.sessionId
    }

    // Toolbar drag state. Two `Animatable<Float>` instances so we can:
    //  - `snapTo` during drag (no animation, finger-tracking)
    //  - `animateTo` on drag-end to spring to the nearest left/right edge
    // Initial value `Float.NaN` means "not yet placed" — once the viewport
    // and toolbar sizes are known, a LaunchedEffect snaps to the default
    // bottom-right corner (or the last user-chosen X-side at fresh y).
    val toolbarX = remember { Animatable(Float.NaN) }
    val toolbarY = remember { Animatable(Float.NaN) }
    var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val hostSource: SourceInfo? = remember(mode) {
        when (mode) {
            is OverlayMode.Window -> SourceInfo(
                file = "${mode.activity.javaClass.simpleName}.kt",
                line = 0,
            )
            is OverlayMode.Embedded -> detectHostSource()
        }
    }
    val accent = persistedSettings.accentColor.color

    val syncApi = remember(persistedSettings.endpoint) {
        persistedSettings.endpoint?.takeIf { it.isNotBlank() && persistedSettings.syncEnabled }
            ?.let { JellyApi(it.trimEnd('/')) }
    }
    DisposableEffect(syncApi) {
        onDispose { syncApi?.close() }
    }

    // Heartbeat to /hello so the local-sync viewer can show device identity +
    // liveness ("Pixel 7 · Android 14 — connected" vs "Last seen 2m ago").
    // Tied to syncApi so it auto-cancels when the endpoint changes or sync is
    // turned off. 12s cadence pairs with the page's 15s manual-probe window
    // so the refresh button always catches at least one ping on a live device.
    LaunchedEffect(syncApi) {
        val api = syncApi ?: return@LaunchedEffect
        val info = DeviceInfo(
            platform = "android",
            model = android.os.Build.MODEL,
            manufacturer = android.os.Build.MANUFACTURER,
            osVersion = android.os.Build.VERSION.RELEASE,
            appName = runCatching {
                context.applicationInfo.loadLabel(context.packageManager).toString()
            }.getOrNull(),
        )
        while (true) {
            runCatching { api.sayHello(info) }
                .onFailure { android.util.Log.w("JellySync", "sayHello heartbeat failed", it) }
            kotlinx.coroutines.delay(12_000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it },
    ) {
        // Saved annotation markers — drawn over content, never block taps.
        if (!state.capturing) {
            val markerHalfPx = with(density) { 14.dp.toPx() }
            annotations.forEachIndexed { index, annotation ->
                val box = annotation.boundingBox ?: return@forEachIndexed
                val cx = box.x + box.width / 2
                val cy = box.y + box.height / 2
                AnnotationMarker(
                    number = index + 1,
                    accent = accent,
                    modifier = Modifier.offset {
                        IntOffset((cx - markerHalfPx).roundToInt(), (cy - markerHalfPx).roundToInt())
                    },
                )
            }
        }

        // Modal capture overlay — live preview while finger is down.
        if (state.annotateMode && state.pending == null && !state.capturing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.04f))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            var currentPos = down.position
                            var hover = doCapture(mode, currentPos, hostSource)
                            state.liveHover = hover
                            var lastHoverBounds = hover?.bounds
                            if (hover != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    if (change.position != currentPos) {
                                        currentPos = change.position
                                        hover = doCapture(mode, currentPos, hostSource)
                                        state.liveHover = hover
                                        val newBounds = hover?.bounds
                                        if (newBounds != null && newBounds != lastHoverBounds) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            lastHoverBounds = newBounds
                                        }
                                    }
                                    change.consume()
                                } else {
                                    change.consume()
                                    val finalElement = hover
                                    val finalPos = currentPos
                                    state.liveHover = null
                                    if (finalElement != null) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val pendingId = UUID.randomUUID().toString()
                                        scope.launch {
                                            val shotPath = if (config.captureScreenshots) {
                                                state.capturing = true
                                                withFrameNanos { }
                                                withFrameNanos { }
                                                try {
                                                    when (mode) {
                                                        is OverlayMode.Window -> Screenshot.captureWindow(
                                                            context = context,
                                                            window = captureWindow,
                                                            decorView = captureView,
                                                            annotationId = pendingId,
                                                        )
                                                        is OverlayMode.Embedded -> Screenshot.captureFullWindow(
                                                            context = context,
                                                            window = captureWindow,
                                                            rootView = captureView,
                                                            annotationId = pendingId,
                                                        )
                                                    }
                                                } finally {
                                                    state.capturing = false
                                                }
                                            } else null
                                            state.pending = PendingCapture(
                                                id = pendingId,
                                                captured = finalElement,
                                                rawOffset = finalPos,
                                                screenshotPath = shotPath,
                                            )
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    },
            )
        }

        // Live preview stroke rectangle.
        if (state.annotateMode && !state.capturing) {
            state.liveHover?.let { hover ->
                LiveHoverOverlay(
                    bounds = hover.bounds,
                    label = hover.displayName,
                    accent = accent,
                )
            }
        }

        // Comment popup — wrapped in our internal dark theme regardless of host.
        state.pending?.let { p ->
            JellyTheme {
                AnnotationPopup(
                    captured = p.captured,
                    screenshotPath = p.screenshotPath,
                    accent = accent,
                    onCancel = {
                        // Reference-identity guard: if the popup was already
                        // dismissed (cancel-then-quick-tap, or recomposition
                        // double-fire), don't double-delete or stomp on a
                        // newer pending capture.
                        if (state.pending !== p) return@AnnotationPopup
                        state.pending = null
                        p.screenshotPath?.let { runCatching { java.io.File(it).delete() } }
                    },
                    onSubmit = { submission ->
                        // Same guard, with a critical second purpose: clear
                        // state.pending BEFORE launching the coroutine so a
                        // second tap on Submit doesn't kick off a second
                        // submission for the same PendingCapture. The async
                        // work below uses `p` captured from the closure, so
                        // dismissing the popup early is safe.
                        if (state.pending !== p) return@AnnotationPopup
                        state.pending = null
                        scope.launch {
                            val bakedPath = p.screenshotPath?.let { rawPath ->
                                val box = p.captured.bounds
                                val outFile = java.io.File(
                                    context.cacheDir,
                                    "jelly/${p.id}-baked.webp",
                                )
                                val baked = BakedImage.bake(
                                    rawPath = rawPath,
                                    bounds = BoundingBox(
                                        x = box.left,
                                        y = box.top,
                                        width = box.width,
                                        height = box.height,
                                    ),
                                    accentArgb = accent.toArgb(),
                                    elementName = p.captured.displayName,
                                    elementPath = p.captured.elementPath,
                                    sourceFile = p.captured.sourceFile,
                                    comment = submission.comment,
                                    intent = submission.intent?.name?.lowercase(),
                                    severity = submission.severity?.name?.lowercase(),
                                    outFile = outFile,
                                    densityScale = density.density,
                                )
                                if (baked != null) {
                                    runCatching { java.io.File(rawPath).delete() }
                                }
                                baked ?: rawPath
                            }
                            val annotation = buildAnnotation(
                                id = p.id,
                                captured = p.captured,
                                rawOffset = p.rawOffset,
                                submission = submission,
                                screenKey = screenKey,
                                viewportWidth = captureView.width,
                                screenshotPath = bakedPath,
                            )
                            val synced = syncApi?.let { api ->
                                runCatching {
                                    val sid = state.activeSessionId
                                        ?: api.createSession(url = "screen:$screenKey").id
                                            .also { state.activeSessionId = it }
                                    val s = api.syncAnnotation(sid, annotation.copy(sessionId = sid))
                                        .copy(syncedTo = sid)
                                    bakedPath?.let { path ->
                                        runCatching {
                                            val file = java.io.File(path)
                                            if (file.exists()) {
                                                val ct = if (path.endsWith(".webp", true)) "image/webp" else "image/png"
                                                api.uploadAnnotationImage(s.id, file.readBytes(), ct)
                                            }
                                        }.onFailure {
                                            android.util.Log.w("JellySync", "image upload failed for id=${s.id}", it)
                                        }
                                    }
                                    s
                                }
                                    .onFailure { android.util.Log.w("JellySync", "real-time sync failed for id=${annotation.id}", it) }
                                    .getOrNull()
                            }
                            val finalAnnotation = synced ?: annotation
                            val updated = annotations + finalAnnotation
                            store.save(screenKey, updated)
                            onAnnotationAdd(finalAnnotation)
                        }
                    },
                )
            }
        }

        // Toolbar — only rendered inline when [renderToolbar] is true. The
        // install path moves the toolbar to its own [TYPE_APPLICATION_PANEL]
        // window (see [JellyToolbarContent]) so it stays clickable
        // when bottom sheets / dialogs are showing; in that mode this
        // block is skipped.
        //
        // Hidden during capture so it isn't baked into the screenshot.
        if (renderToolbar && !state.capturing) {
            val padPx = with(density) { 20.dp.toPx() }

            // Initial placement (and re-placement when viewport/toolbar
            // size first becomes known): bottom-right with a 20.dp inset.
            // Skipped after that so the user's drag position survives
            // recompositions.
            LaunchedEffect(viewportSize, toolbarSize) {
                if (toolbarX.value.isNaN() &&
                    viewportSize.width > 0 && toolbarSize.width > 0
                ) {
                    val initX = (viewportSize.width - toolbarSize.width - padPx)
                        .coerceAtLeast(0f)
                    val initY = (viewportSize.height - toolbarSize.height - padPx)
                        .coerceAtLeast(0f)
                    toolbarX.snapTo(initX)
                    toolbarY.snapTo(initY)
                }
            }

            Box(
                modifier = Modifier
                    .offset {
                        // Until first measurement, hide off-screen so we
                        // don't briefly flash at (0,0).
                        if (toolbarX.value.isNaN()) {
                            IntOffset(viewportSize.width, viewportSize.height)
                        } else {
                            IntOffset(toolbarX.value.toInt(), toolbarY.value.toInt())
                        }
                    }
                    .onSizeChanged { toolbarSize = it }
                    .pointerInput(viewportSize) {
                        // detectDragGestures waits for touch slop before
                        // claiming the gesture, so taps on the inner chip /
                        // tool buttons still fire normally — only sustained
                        // drags relocate the FAB.
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val maxX = (viewportSize.width - toolbarSize.width)
                                    .toFloat().coerceAtLeast(0f)
                                val maxY = (viewportSize.height - toolbarSize.height)
                                    .toFloat().coerceAtLeast(0f)
                                scope.launch {
                                    toolbarX.snapTo(
                                        (toolbarX.value + dragAmount.x).coerceIn(0f, maxX),
                                    )
                                    toolbarY.snapTo(
                                        (toolbarY.value + dragAmount.y).coerceIn(0f, maxY),
                                    )
                                }
                            },
                            onDragEnd = {
                                // Snap X to the nearer edge. Y stays at
                                // the user's chosen height (already clamped
                                // during drag).
                                val maxX = (viewportSize.width - toolbarSize.width)
                                    .toFloat().coerceAtLeast(0f)
                                val midX = maxX / 2f
                                val targetX = if (toolbarX.value < midX) padPx
                                else (maxX - padPx).coerceAtLeast(0f)
                                scope.launch {
                                    toolbarX.animateTo(
                                        targetValue = targetX,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            },
                        )
                    },
            ) {
                JellyTheme {
                    AnnotationToolbar(
                        annotateMode = state.annotateMode,
                        annotationCount = annotations.size,
                        accent = accent,
                        onToggleAnnotate = { state.annotateMode = !state.annotateMode },
                        onOpenReview = { state.reviewOpen = true },
                        onSettings = { state.settingsOpen = true },
                        onClose = { state.annotateMode = false },
                    )
                }
            }
        }

        if (state.settingsOpen) {
            // Manual-sync state. Recomputed when the sheet opens (and again
            // after each push) so the count reflects every annotation currently
            // on the device across all screen keys — re-push uploads them all.
            var catchUp by remember { mutableStateOf(CatchUpSyncStatus()) }
            // Full reset on endpoint change. Without this, a "Pushed 5 of 5"
            // result from the previous endpoint sticks around when the user
            // pastes a new URL — confusing because that count is no longer
            // relevant to the new room.
            LaunchedEffect(persistedSettings.endpoint) {
                catchUp = CatchUpSyncStatus()
            }
            LaunchedEffect(state.settingsOpen, persistedSettings.endpoint, annotations) {
                if (!state.settingsOpen) return@LaunchedEffect
                val total = store.enumerateAll().values.sumOf { it.size }
                catchUp = catchUp.copy(storedCount = total)
            }

            JellyTheme {
                SettingsSheet(
                    settings = persistedSettings,
                    onSettingsChange = { next ->
                        scope.launch { settingsStore.update { next } }
                    },
                    onDismiss = { state.settingsOpen = false },
                    catchUpStatus = catchUp,
                    onScanQr = {
                        // Launch the GMS code scanner. On success, replace the
                        // endpoint with the scanned URL (trimmed of trailing
                        // slash so /sessions/... requests don't double up) and
                        // flip Sync on — if you scanned, you obviously want it.
                        dev.jelly.qr.QrScanner.scan(
                            context = context,
                            onResult = { url ->
                                val clean = url.trim().trimEnd('/')
                                scope.launch {
                                    settingsStore.update {
                                        it.copy(endpoint = clean, syncEnabled = true)
                                    }
                                }
                            },
                            onError = { /* swallow — user can still type the URL */ },
                        )
                    },
                    onPushPending = {
                        val api = syncApi ?: return@SettingsSheet
                        if (catchUp.isPushing) return@SettingsSheet
                        catchUp = catchUp.copy(isPushing = true, lastResult = null)
                        scope.launch {
                            val result = pushAllAnnotations(store, api)
                            val total = store.enumerateAll().values.sumOf { it.size }
                            val msg = when {
                                result.isEmpty -> "No annotations on this device."
                                result.allSucceeded -> "Pushed ${result.synced} of ${result.attempted}."
                                result.synced == 0 -> "Couldn't reach endpoint — ${result.failed} failed."
                                else -> "Pushed ${result.synced}, ${result.failed} failed."
                            }
                            catchUp = catchUp.copy(
                                isPushing = false,
                                storedCount = total,
                                lastResult = msg,
                            )
                        }
                    },
                )
            }
        }

        if (state.reviewOpen) {
            JellyTheme {
                AnnotationsScreen(
                    annotations = annotations,
                    accent = accent,
                    onDismiss = { state.reviewOpen = false },
                    onCopyAll = {
                        val md = OutputGenerator(
                            viewportWidth = captureView.width,
                            viewportHeight = captureView.height,
                        ).generate(annotations, screenKey, persistedSettings.detailLevel)
                        if (config.copyToClipboard) copyToClipboard(context, md)
                        onCopy(md)
                    },
                    onShareAll = {
                        val md = OutputGenerator(
                            viewportWidth = captureView.width,
                            viewportHeight = captureView.height,
                        ).generate(annotations, screenKey, persistedSettings.detailLevel)
                        shareAnnotations(context, md, annotations)
                    },
                    onClearAll = {
                        scope.launch {
                            val cleared = annotations
                            cleared.forEach { a ->
                                a.screenshotPath?.let { runCatching { java.io.File(it).delete() } }
                            }
                            store.save(screenKey, emptyList())
                            onAnnotationsClear(cleared)
                        }
                    },
                    onDeleteOne = { a ->
                        scope.launch {
                            a.screenshotPath?.let { runCatching { java.io.File(it).delete() } }
                            store.save(screenKey, annotations.filterNot { it.id == a.id })
                            onAnnotationDelete(a)
                        }
                    },
                    onShareOne = { a ->
                        val md = OutputGenerator(
                            viewportWidth = captureView.width,
                            viewportHeight = captureView.height,
                        ).generate(listOf(a), screenKey, persistedSettings.detailLevel)
                        shareAnnotations(context, md, listOf(a))
                    },
                )
            }
        }
    }
}

/**
 * Toolbar-only composable for the install path's separate window.
 * Hosted in a `TYPE_APPLICATION_PANEL` window (z=1000) so it stays
 * clickable above bottom sheets / dialogs (`TYPE_APPLICATION` z=2).
 *
 * Drag is handled at the **window** level (via [onDrag] / [onDragEnd]
 * callbacks that the controller wires up to update the window's
 * `WindowManager.LayoutParams`), not via Compose's `Modifier.offset`.
 * That's because the window itself wraps the toolbar's bounds — moving
 * the window moves the toolbar; Compose can't "see" it. The Compose
 * tree just emits drag deltas.
 */
@Composable
internal fun JellyToolbarContent(
    config: JellyConfig,
    activity: Activity,
    state: JellyOverlayState,
    onDrag: (deltaX: Float, deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { AnnotationStore(context.applicationContext) }
    val settingsStore = remember(context) { SettingsStore(context.applicationContext) }
    val screenKey = remember { activity.javaClass.simpleName }
    val annotations by store.observe(screenKey).collectAsState(initial = emptyList())
    val persistedSettings by settingsStore.settings.collectAsState(
        initial = Settings(
            detailLevel = config.detailLevel,
            accentColor = config.accentColor,
            syncEnabled = config.endpoint != null,
            endpoint = config.endpoint,
        ),
    )
    val accent = persistedSettings.accentColor.color

    if (state.capturing) return

    JellyTheme {
        AnnotationToolbar(
            annotateMode = state.annotateMode,
            annotationCount = annotations.size,
            accent = accent,
            onToggleAnnotate = { state.annotateMode = !state.annotateMode },
            onOpenReview = { state.reviewOpen = true },
            onSettings = { state.settingsOpen = true },
            onClose = { state.annotateMode = false },
            modifier = Modifier.pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = onDragEnd,
                )
            },
        )
    }
}

private fun doCapture(
    mode: OverlayMode,
    pointInOverlay: Offset,
    hostSource: SourceInfo?,
): CapturedElement? = when (mode) {
    is OverlayMode.Window -> SemanticsCapture.captureInWindow(
        decorView = mode.activity.window.decorView,
        // Overlay fills decorView, so overlay-local point == window point.
        pointInWindow = pointInOverlay,
        compositionInspector = resolveCompositionInspector(),
        hostSource = hostSource,
    )
    is OverlayMode.Embedded -> SemanticsCapture.capture(
        rootView = mode.rootView,
        pointInRoot = pointInOverlay,
        compositionInspector = resolveCompositionInspector(),
        hostSource = hostSource,
    )
}

internal data class PendingCapture(
    val id: String,
    val captured: CapturedElement,
    val rawOffset: Offset,
    val screenshotPath: String?,
)

private fun buildAnnotation(
    id: String,
    captured: CapturedElement,
    rawOffset: Offset,
    submission: PopupSubmission,
    screenKey: String,
    viewportWidth: Int,
    screenshotPath: String?,
): Annotation {
    val box = captured.bounds
    val accessibilityBits = buildList {
        captured.role?.let { add("role=$it") }
        captured.contentDescription?.let { add("contentDescription=\"$it\"") }
        captured.testTag?.let { add("testTag=$it") }
        captured.stateDescription?.let { add("state=$it") }
    }.takeIf { it.isNotEmpty() }?.joinToString(", ")

    return Annotation(
        id = id,
        x = if (viewportWidth > 0) (rawOffset.x / viewportWidth.toFloat()) * 100f else 0f,
        y = rawOffset.y,
        comment = submission.comment,
        element = captured.displayName,
        elementPath = captured.elementPath,
        timestamp = System.currentTimeMillis(),
        boundingBox = BoundingBox(
            x = box.left,
            y = box.top,
            width = box.width,
            height = box.height,
        ),
        accessibility = accessibilityBits,
        nearbyElements = captured.nearbyElements,
        nearbyText = captured.nearbyText,
        composableHierarchy = captured.composableName,
        sourceFile = captured.sourceFile,
        intent = submission.intent,
        severity = submission.severity,
        screenshotPath = screenshotPath,
        url = "screen:$screenKey",
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Jelly", text))
}

private fun shareAnnotations(
    context: Context,
    markdown: String,
    annotations: List<Annotation>,
) {
    val shareText = markdown
    copyToClipboard(context, shareText)
    android.widget.Toast.makeText(
        context,
        "Comment copied — paste in your message",
        android.widget.Toast.LENGTH_SHORT,
    ).show()

    val authority = "${context.packageName}.jelly.fileprovider"
    val uris = ArrayList<android.net.Uri>()
    annotations.forEach { a ->
        val path = a.screenshotPath ?: return@forEach
        val file = java.io.File(path)
        if (!file.exists()) return@forEach
        runCatching {
            androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        }.getOrNull()?.let { uris += it }
    }

    val intent = when {
        uris.isEmpty() -> Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        uris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, uris[0])
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, annotations.first().element)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "Annotation", uris[0]).apply {
                addItem(ClipData.Item(shareText))
            }
        }
        else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/webp"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "Annotations", uris[0]).apply {
                for (i in 1 until uris.size) addItem(ClipData.Item(uris[i]))
                addItem(ClipData.Item(shareText))
            }
        }
    }

    context.startActivity(
        Intent.createChooser(intent, "Share annotations").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

internal fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
