package dev.agentation

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import dev.agentation.capture.BakedImage
import dev.agentation.capture.CapturedElement
import dev.agentation.capture.Screenshot
import dev.agentation.capture.SemanticsCapture
import dev.agentation.capture.resolveCompositionInspector
import dev.agentation.model.Annotation
import dev.agentation.model.BoundingBox
import dev.agentation.output.OutputGenerator
import dev.agentation.storage.AnnotationStore
import dev.agentation.storage.Settings
import dev.agentation.storage.SettingsStore
import dev.agentation.sync.AgentationApi
import dev.agentation.theme.AgentationTheme
import dev.agentation.ui.AnnotationMarker
import dev.agentation.ui.AnnotationPopup
import dev.agentation.ui.AnnotationToolbar
import dev.agentation.ui.AnnotationsScreen
import dev.agentation.ui.LiveHoverOverlay
import dev.agentation.ui.PopupSubmission
import dev.agentation.ui.SettingsSheet
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Wraps a Compose app's root with the Agentation overlay.
 *
 * Usage:
 * ```
 * setContent {
 *     Agentation(config = AgentationConfig(endpoint = MCP_URL)) {
 *         MyAppRoot()
 *     }
 * }
 * ```
 *
 * While annotate-mode is on, the wrapper consumes long-press gestures and
 * inspects the semantics tree at the press point — same modal UX as the
 * web `<Agentation />` toolbar.
 */
@Composable
fun Agentation(
    config: AgentationConfig = AgentationConfig(),
    onAnnotationAdd: (Annotation) -> Unit = {},
    onAnnotationDelete: (Annotation) -> Unit = {},
    onAnnotationsClear: (List<Annotation>) -> Unit = {},
    onCopy: (String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { AnnotationStore(context.applicationContext) }
    val settingsStore = remember(context) { SettingsStore(context.applicationContext) }
    val screenKey = remember(config.screenKey) {
        config.screenKey ?: (context.findActivity()?.javaClass?.simpleName ?: "default")
    }
    val annotations by store.observe(screenKey).collectAsState(initial = emptyList())
    val persistedSettings by settingsStore.settings.collectAsState(
        initial = Settings(
            detailLevel = config.detailLevel,
            accentColor = config.accentColor,
            syncEnabled = config.endpoint != null,
            endpoint = config.endpoint,
            webhookUrl = config.webhookUrl,
        ),
    )

    var annotateMode by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingCapture?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var reviewOpen by remember { mutableStateOf(false) }
    var activeSessionId by remember { mutableStateOf(config.sessionId) }
    // While true, suppress the toolbar / markers / modal tint so they're not
    // baked into the screenshot. Recomposition + a two-frame wait flushes the
    // hidden state to the platform surface before PixelCopy reads it.
    var capturing by remember { mutableStateOf(false) }
    // Currently-hovered element while finger is down in annotate mode. Drives
    // the live stroke rectangle preview so users can see (and drag-adjust)
    // their selection before releasing.
    var liveHover by remember { mutableStateOf<CapturedElement?>(null) }
    val accent = persistedSettings.accentColor.color

    val syncApi = remember(persistedSettings.endpoint) {
        persistedSettings.endpoint?.takeIf { it.isNotBlank() && persistedSettings.syncEnabled }
            ?.let { AgentationApi(it.trimEnd('/')) }
    }
    DisposableEffect(syncApi) {
        onDispose { syncApi?.close() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Host content
        content()

        // Saved annotation markers (drawn over content, never block taps).
        // Hidden during capture so they don't appear in the screenshot.
        if (!capturing) {
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

        // Modal capture overlay — live preview while finger is down, commits on release.
        if (annotateMode && pending == null && !capturing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.04f))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            var currentPos = down.position
                            var hover = SemanticsCapture.capture(
                                rootView = view,
                                pointInRoot = currentPos,
                                compositionInspector = resolveCompositionInspector(),
                            )
                            liveHover = hover
                            // Track the last bounds we vibrated for so the tick
                            // only fires when the hovered element actually changes.
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
                                        hover = SemanticsCapture.capture(
                                            rootView = view,
                                            pointInRoot = currentPos,
                                            compositionInspector = resolveCompositionInspector(),
                                        )
                                        liveHover = hover
                                        val newBounds = hover?.bounds
                                        if (newBounds != null && newBounds != lastHoverBounds) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            lastHoverBounds = newBounds
                                        }
                                    }
                                    change.consume()
                                } else {
                                    // Release — commit selection.
                                    change.consume()
                                    val finalElement = hover
                                    val finalPos = currentPos
                                    liveHover = null
                                    if (finalElement != null) {
                                        // Confirm tick — slightly heavier than the
                                        // selection-changed tick so users feel a
                                        // distinct "selected!" beat.
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val pendingId = UUID.randomUUID().toString()
                                        scope.launch {
                                            val shotPath = if (config.captureScreenshots) {
                                                capturing = true
                                                withFrameNanos { }
                                                withFrameNanos { }
                                                try {
                                                    Screenshot.captureFullWindow(
                                                        context = context,
                                                        window = context.findActivity()?.window,
                                                        rootView = view,
                                                        annotationId = pendingId,
                                                    )
                                                } finally {
                                                    capturing = false
                                                }
                                            } else null
                                            pending = PendingCapture(
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

        // Live preview overlay — stroke rectangle on the currently-hovered
        // element while finger is down. Hidden during the post-release capture.
        if (annotateMode && !capturing) {
            liveHover?.let { hover ->
                LiveHoverOverlay(
                    bounds = hover.bounds,
                    label = hover.displayName,
                    accent = accent,
                )
            }
        }

        // Comment popup — rendered in our internal dark theme, regardless of host.
        pending?.let { p ->
            AgentationTheme {
            AnnotationPopup(
                captured = p.captured,
                screenshotPath = p.screenshotPath,
                accent = accent,
                onCancel = {
                    // Best-effort cleanup of the captured thumbnail if user cancels.
                    p.screenshotPath?.let { runCatching { java.io.File(it).delete() } }
                    pending = null
                },
                onSubmit = { submission ->
                    scope.launch {
                        // Bake the stroke rectangle + comment caption into a
                        // self-contained image so the file is shareable to apps
                        // that drop EXTRA_TEXT (Slack, WhatsApp, etc).
                        val bakedPath = p.screenshotPath?.let { rawPath ->
                            val box = p.captured.bounds
                            val outFile = java.io.File(
                                context.cacheDir,
                                "agentation/${p.id}-baked.webp",
                            )
                            val baked = BakedImage.bake(
                                rawPath = rawPath,
                                bounds = dev.agentation.model.BoundingBox(
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
                            // Keep only the baked file; the raw is no longer needed.
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
                            view = view,
                            screenshotPath = bakedPath,
                        )
                        val synced = syncApi?.let { api ->
                            runCatching {
                                val sid = activeSessionId
                                    ?: api.createSession(url = "screen:$screenKey").id
                                        .also { activeSessionId = it }
                                api.syncAnnotation(sid, annotation.copy(sessionId = sid))
                                    .copy(syncedTo = sid)
                            }.getOrNull()
                        }
                        val finalAnnotation = synced ?: annotation
                        val updated = annotations + finalAnnotation
                        store.save(screenKey, updated)
                        onAnnotationAdd(finalAnnotation)
                        pending = null
                    }
                },
            )
            }
        }

        // Toolbar — bottom-right. Hidden during capture so it isn't in the screenshot.
        if (!capturing) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                AgentationTheme {
                    AnnotationToolbar(
                        annotateMode = annotateMode,
                        annotationCount = annotations.size,
                        accent = accent,
                        onToggleAnnotate = { annotateMode = !annotateMode },
                        onOpenReview = { reviewOpen = true },
                        onSettings = { settingsOpen = true },
                        onClose = { annotateMode = false },
                    )
                }
            }
        }

        if (settingsOpen) {
            AgentationTheme {
                SettingsSheet(
                    settings = persistedSettings,
                    onSettingsChange = { next ->
                        scope.launch { settingsStore.update { next } }
                    },
                    onDismiss = { settingsOpen = false },
                )
            }
        }

        if (reviewOpen) {
            AgentationTheme {
            AnnotationsScreen(
                annotations = annotations,
                accent = accent,
                onDismiss = { reviewOpen = false },
                onCopyAll = {
                    val md = OutputGenerator(
                        viewportWidth = view.width,
                        viewportHeight = view.height,
                    ).generate(annotations, screenKey, persistedSettings.detailLevel)
                    if (config.copyToClipboard) copyToClipboard(context, md)
                    onCopy(md)
                },
                onShareAll = {
                    val md = OutputGenerator(
                        viewportWidth = view.width,
                        viewportHeight = view.height,
                    ).generate(annotations, screenKey, persistedSettings.detailLevel)
                    shareAnnotations(context, md, annotations)
                },
                onClearAll = {
                    scope.launch {
                        val cleared = annotations
                        // Best-effort cleanup of cached screenshots.
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
                        viewportWidth = view.width,
                        viewportHeight = view.height,
                    ).generate(listOf(a), screenKey, persistedSettings.detailLevel)
                    shareAnnotations(context, md, listOf(a))
                },
            )
            }
        }
    }
}

private data class PendingCapture(
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
    view: android.view.View,
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
        x = if (view.width > 0) (rawOffset.x / view.width.toFloat()) * 100f else 0f,
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
    cm.setPrimaryClip(ClipData.newPlainText("Agentation", text))
}

private fun shareAnnotations(
    context: Context,
    markdown: String,
    annotations: List<Annotation>,
) {
    // Use the OutputGenerator markdown directly — it has proper **Location:**,
    // **Source:**, **Feedback:** headings and respects the user's chosen detail
    // level. Single source of truth for "what does an annotation read like as
    // text", whether copied or shared.
    val shareText = markdown

    // Slack and other image-handling apps drop EXTRA_TEXT when an image is
    // attached. Pre-copy the text to clipboard so the user can paste into the
    // message after the image attaches. Belt-and-suspenders: still send the
    // text via EXTRA_TEXT + ClipData for apps that DO honor it (Gmail, Drive).
    copyToClipboard(context, shareText)
    android.widget.Toast.makeText(
        context,
        "Comment copied — paste in your message",
        android.widget.Toast.LENGTH_SHORT,
    ).show()

    val authority = "${context.packageName}.agentation.fileprovider"
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
            // ClipData with both items — receivers that read ClipData get both
            // the image and the text as separate items.
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

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

