package dev.agentation

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.agentation.capture.CapturedElement
import dev.agentation.capture.Screenshot
import dev.agentation.capture.SemanticsCapture
import dev.agentation.capture.resolveCompositionInspector
import dev.agentation.model.Annotation
import dev.agentation.model.BoundingBox
import dev.agentation.output.OutputGenerator
import dev.agentation.storage.AnnotationStore
import dev.agentation.ui.AnnotationMarker
import dev.agentation.ui.AnnotationPopup
import dev.agentation.ui.AnnotationToolbar
import dev.agentation.ui.PopupSubmission
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
    val scope = rememberCoroutineScope()
    val store = remember(context) { AnnotationStore(context.applicationContext) }
    val screenKey = remember(config.screenKey) {
        config.screenKey ?: (context.findActivity()?.javaClass?.simpleName ?: "default")
    }
    val annotations by store.observe(screenKey).collectAsState(initial = emptyList())

    var annotateMode by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingCapture?>(null) }
    val accent = config.accentColor.color

    Box(modifier = Modifier.fillMaxSize()) {
        // Host content
        content()

        // Saved annotation markers (drawn over content, never block taps)
        val markerHalfPx = with(density) { 12.dp.toPx() }
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

        // Modal long-press capture overlay (only present when annotate mode is on)
        if (annotateMode && pending == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.04f))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { offset ->
                                val captured = SemanticsCapture.capture(
                                    rootView = view,
                                    pointInWindow = offset,
                                    compositionInspector = resolveCompositionInspector(),
                                )
                                if (captured != null) {
                                    pending = PendingCapture(
                                        captured = captured,
                                        rawOffset = offset,
                                    )
                                }
                            },
                        )
                    },
            )
        }

        // Comment popup
        pending?.let { p ->
            AnnotationPopup(
                captured = p.captured,
                accent = accent,
                onCancel = { pending = null },
                onSubmit = { submission ->
                    scope.launch {
                        val annotation = buildAnnotation(
                            captured = p.captured,
                            rawOffset = p.rawOffset,
                            submission = submission,
                            screenKey = screenKey,
                            view = view,
                            context = context,
                            captureScreenshot = config.captureScreenshots,
                        )
                        val updated = annotations + annotation
                        store.save(screenKey, updated)
                        onAnnotationAdd(annotation)
                        pending = null
                    }
                },
            )
        }

        // Toolbar — bottom-right
        AnnotationToolbar(
            annotateMode = annotateMode,
            annotationCount = annotations.size,
            accent = accent,
            onToggleAnnotate = { annotateMode = !annotateMode },
            onCopy = {
                val md = OutputGenerator(
                    viewportWidth = view.width,
                    viewportHeight = view.height,
                ).generate(annotations, screenKey, config.detailLevel)
                if (config.copyToClipboard) copyToClipboard(context, md)
                onCopy(md)
            },
            onShare = {
                val md = OutputGenerator(
                    viewportWidth = view.width,
                    viewportHeight = view.height,
                ).generate(annotations, screenKey, config.detailLevel)
                shareText(context, md)
            },
            onClear = {
                scope.launch {
                    val cleared = annotations
                    store.save(screenKey, emptyList())
                    onAnnotationsClear(cleared)
                }
            },
            onClose = { annotateMode = false },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        )
    }
}

private data class PendingCapture(
    val captured: CapturedElement,
    val rawOffset: Offset,
)

private suspend fun buildAnnotation(
    captured: CapturedElement,
    rawOffset: Offset,
    submission: PopupSubmission,
    screenKey: String,
    view: android.view.View,
    context: Context,
    captureScreenshot: Boolean,
): Annotation {
    val id = UUID.randomUUID().toString()
    val box = captured.boundsInWindow
    val accessibilityBits = buildList {
        captured.role?.let { add("role=$it") }
        captured.contentDescription?.let { add("contentDescription=\"$it\"") }
        captured.testTag?.let { add("testTag=$it") }
        captured.stateDescription?.let { add("state=$it") }
    }.takeIf { it.isNotEmpty() }?.joinToString(", ")

    val screenshotPath = if (captureScreenshot) {
        val window = context.findActivity()?.window
        Screenshot.captureRegion(
            context = context,
            window = window,
            rootView = view,
            regionInWindow = box,
            annotationId = id,
        )
    } else null

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

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share annotations").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

