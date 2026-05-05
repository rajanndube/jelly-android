package dev.jelly

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import dev.jelly.model.Annotation

/**
 * Wraps a Compose tree with the Jelly toolbar overlay.
 *
 * **Prefer [Jelly.install] over this composable for new integrations.**
 * `install()` attaches a single overlay to every activity's decor view, so
 * it sees every Compose root in the window — that's the only mode that
 * works for apps spanning multiple `setContent` calls, dialogs, or
 * View-based hosts. This composable wrapper only sees the single
 * AndroidComposeView it lives in, which is enough for simple single-root
 * apps but breaks down once anything in the host renders into a separate
 * window.
 *
 * Usage (legacy):
 * ```
 * setContent {
 *     Jelly(config = JellyConfig(endpoint = MCP_URL)) {
 *         MyAppRoot()
 *     }
 * }
 * ```
 *
 * Usage (preferred — Application-level install):
 * ```
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         if (BuildConfig.DEBUG) Jelly.install(this)
 *     }
 * }
 * ```
 */
@Composable
fun Jelly(
    config: JellyConfig = JellyConfig(),
    onAnnotationAdd: (Annotation) -> Unit = {},
    onAnnotationDelete: (Annotation) -> Unit = {},
    onAnnotationsClear: (List<Annotation>) -> Unit = {},
    onCopy: (String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = context.findActivity()?.window

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        JellyOverlayContent(
            config = config,
            mode = OverlayMode.Embedded(rootView = view, window = window),
            onAnnotationAdd = onAnnotationAdd,
            onAnnotationDelete = onAnnotationDelete,
            onAnnotationsClear = onAnnotationsClear,
            onCopy = onCopy,
        )
    }
}
