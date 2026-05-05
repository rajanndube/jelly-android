package dev.jelly

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.WeakHashMap

/**
 * Singleton tag attached to every overlay [ComposeView] we install.
 * `SemanticsCapture.captureInWindow` uses this to recognize and skip our
 * own subtree when walking decor view for Compose roots — without this,
 * we'd hit-test our own overlay first, find an empty synthetic root that
 * spans the full screen, and report "the whole window" as the selected
 * element.
 */
internal object JellyOverlayMarker

/**
 * Backend for `Jelly.install()` — listens to the host application's
 * activity lifecycle and attaches **two** ComposeViews to each activity:
 *
 *  1. A **capture overlay** added to the activity's `decorView`. Renders
 *     marker dots, the modal capture, popup, settings and review screens.
 *     Lives at the same z-level as the activity content.
 *  2. A **toolbar overlay** added to its own [WindowManager] window with
 *     `TYPE_APPLICATION_PANEL` (z=1000). Standard `Dialog`s — including
 *     `BottomSheetDialog` — use `TYPE_APPLICATION` (z=2), so the toolbar
 *     stays clickable and visible above bottom sheets, alert dialogs, and
 *     anything else dialog-shaped. Without this, tapping the FAB while
 *     a sheet was up would actually hit the sheet's outside-tap dismiss
 *     scrim, and the FAB itself would be visually behind it.
 *
 * Both ComposeViews share a single [JellyOverlayState] so a tap on
 * the FAB (in the panel window) flips `annotateMode` for the capture
 * overlay (in the decor view).
 */
internal class ActivityOverlayController(
    private val config: JellyConfig,
) : Application.ActivityLifecycleCallbacks {

    private val attached = WeakHashMap<Activity, AttachedActivity>()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        attach(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        attach(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        // Re-install the focus bumper in case the host (AppCompat,
        // Hilt, or anything else that wraps Window.Callback after
        // onCreate) replaced ours. The wrapper is idempotent — already-
        // wrapped windows are no-op'd.
        attached[activity]?.let { installFocusBumper(activity, it) }
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        detach(activity)
    }

    fun detachAll() {
        attached.keys.toList().forEach { detach(it) }
    }

    private fun attach(activity: Activity) {
        if (attached.containsKey(activity)) return
        val decor = activity.window?.decorView as? ViewGroup ?: return

        val state = JellyOverlayState()

        // 1. Capture overlay (markers / modal / popup / sheets) → decor view.
        val captureView = ComposeView(activity).apply {
            tag = JellyOverlayMarker
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool,
            )
            setContent {
                JellyOverlayContent(
                    config = config,
                    mode = OverlayMode.Window(activity),
                    sharedState = state,
                    renderToolbar = false,
                )
            }
        }
        decor.addView(
            captureView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        // 2. Toolbar overlay → its own TYPE_APPLICATION_PANEL window, sized
        //    to wrap just the FAB so touches outside the toolbar pass
        //    through to whatever's underneath (the activity, a bottom
        //    sheet, etc.). MATCH_PARENT here would block all touches.
        val toolbarView = ComposeView(activity).apply {
            tag = JellyOverlayMarker
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool,
            )
        }
        val padPx = (20 * activity.resources.displayMetrics.density).toInt()
        val toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            // TYPE_APPLICATION_PANEL — a sub-window of the activity.
            // We can't use TYPE_APPLICATION here: a TYPE_APPLICATION
            // window expects a *fresh* app-window token, but our token
            // is the activity's token (which already hosts the
            // activity's main window) and `addView` fails silently with
            // BadTokenException. The trade-off is that
            // TYPE_APPLICATION_PANEL's layer is `parent.layer + small`,
            // so it sits *below* sibling Dialog-class windows
            // (BottomSheet, AlertDialog, etc.) — those still cover the
            // FAB. That is a known limitation; the only permission-free
            // fix would be to host the FAB inside a Dialog of our own
            // (which gets a fresh token and shares the dialog layer
            // space) and re-show it when focus is lost. SYSTEM_ALERT_WINDOW
            // permission would also work but is invasive.
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // `BOTTOM | END` makes (x, y) read as insets from the bottom-right
            // corner — straightforward initial position. The drag math
            // converts: dragging right increases the toolbar's screen-x,
            // which means *decreasing* its right-inset. Same for y.
            gravity = Gravity.BOTTOM or Gravity.END
            x = padPx
            y = padPx
        }

        val attachment = AttachedActivity(
            state = state,
            captureView = captureView,
            toolbarView = toolbarView,
            toolbarParams = toolbarParams,
        )
        attached[activity] = attachment

        // Toolbar content uses callbacks that dispatch back to *this*
        // controller; they read [attachment] by closure to update the
        // window params at runtime.
        toolbarView.setContent {
            JellyToolbarContent(
                config = config,
                activity = activity,
                state = state,
                onDrag = { dx, dy -> onToolbarDrag(activity, dx, dy) },
                onDragEnd = { onToolbarDragEnd(activity) },
            )
        }

        // The window token doesn't exist until the activity's decor view is
        // attached to the WindowManager — which happens *after*
        // `onCreate`. Try immediately; if not ready, defer until the
        // decor view's attach event fires.
        if (decor.windowToken != null) {
            addToolbarWindow(activity, attachment)
        } else {
            decor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    decor.removeOnAttachStateChangeListener(this)
                    addToolbarWindow(activity, attachment)
                }
                override fun onViewDetachedFromWindow(v: View) {}
            })
        }

        // Wrap the activity's Window.Callback so we can detect when the
        // activity loses focus (which happens whenever a Dialog,
        // BottomSheet, etc. is shown above it) and bump our toolbar
        // window back to the top of the WindowManager stack. This is
        // the workaround for `TYPE_APPLICATION_PANEL` not actually
        // sitting above sibling app-window dialogs — `addView` ordering
        // is "last in, on top" within the same app, so we re-add to
        // re-claim the top slot.
        installFocusBumper(activity, attachment)
    }

    /**
     * Wraps `activity.window.callback` with a delegating implementation
     * that also bumps the toolbar above any new window when focus is
     * lost. Idempotent — re-wrapping is detected and skipped.
     */
    private fun installFocusBumper(activity: Activity, attachment: AttachedActivity) {
        val window = activity.window ?: return
        val original = window.callback ?: return
        if (original is FocusBumpCallback) return
        window.callback = FocusBumpCallback(original) { hasFocus ->
            if (!hasFocus) bumpToolbarToTop(activity, attachment)
        }
    }

    /**
     * Re-add the toolbar window so it sits at the top of the
     * `WindowManager` stack. Compose state is preserved across the
     * remove/add because we use a re-entrant `setContent` and the
     * shared [JellyOverlayState] outlives the view itself. A short
     * post defers to the next frame so the focus-stealing window has
     * finished adding before we pop on top.
     */
    private fun bumpToolbarToTop(activity: Activity, attachment: AttachedActivity) {
        if (!attachment.attachedToWindowManager) return
        if (activity.isFinishing || activity.isDestroyed) return
        attachment.toolbarView.post {
            if (!attachment.attachedToWindowManager) return@post
            if (activity.isFinishing || activity.isDestroyed) return@post
            runCatching {
                val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeViewImmediate(attachment.toolbarView)
                wm.addView(attachment.toolbarView, attachment.toolbarParams)
            }
        }
    }

    /**
     * Window.Callback wrapper that delegates everything to the host's
     * original callback (activity-side menu / key / accessibility logic
     * stays intact) but additionally fires [onFocus] on every focus
     * change so we can pop the toolbar to the top.
     */
    private class FocusBumpCallback(
        private val delegate: Window.Callback,
        private val onFocus: (hasFocus: Boolean) -> Unit,
    ) : Window.Callback by delegate {
        override fun onWindowFocusChanged(hasFocus: Boolean) {
            delegate.onWindowFocusChanged(hasFocus)
            onFocus(hasFocus)
        }
    }

    private fun addToolbarWindow(activity: Activity, attachment: AttachedActivity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val token = activity.window?.decorView?.windowToken ?: return

        // ComposeView walks its view-tree ancestors looking for a
        // LifecycleOwner / SavedStateRegistryOwner / ViewModelStoreOwner
        // when it attaches. When we add the toolbar to WindowManager
        // directly (instead of into the activity's decor tree), the
        // toolbar view has no view-tree ancestors, so the lookup fails
        // with `ViewTreeLifecycleOwner not found from ComposeView…`.
        // Setting the owners directly on the toolbar view itself fixes
        // that — Compose finds them at the root of the (one-node) tree.
        // Requires the activity to be a ComponentActivity (or otherwise
        // implement these owner contracts), which is the standard
        // setup; fall back to skipping the toolbar window if not.
        val lifecycleOwner = activity as? LifecycleOwner ?: return
        val savedStateOwner = activity as? SavedStateRegistryOwner ?: return
        attachment.toolbarView.setViewTreeLifecycleOwner(lifecycleOwner)
        attachment.toolbarView.setViewTreeSavedStateRegistryOwner(savedStateOwner)
        (activity as? ViewModelStoreOwner)?.let {
            attachment.toolbarView.setViewTreeViewModelStoreOwner(it)
        }

        attachment.toolbarParams.token = token
        runCatching {
            val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.addView(attachment.toolbarView, attachment.toolbarParams)
            attachment.attachedToWindowManager = true
        }
    }

    /**
     * Live drag — called for every move event from the toolbar's
     * `pointerInput`. `dx`/`dy` are screen-relative deltas (positive x =
     * rightward). With `gravity = BOTTOM | END`, the params x is the
     * inset *from the right edge*, so screen-rightward drag
     * corresponds to *decreasing* params.x. Same for y.
     */
    private fun onToolbarDrag(activity: Activity, dx: Float, dy: Float) {
        val attachment = attached[activity] ?: return
        if (!attachment.attachedToWindowManager) return
        val decor = activity.window?.decorView ?: return
        val toolbarW = attachment.toolbarView.width.coerceAtLeast(1)
        val toolbarH = attachment.toolbarView.height.coerceAtLeast(1)
        val maxX = (decor.width - toolbarW).coerceAtLeast(0)
        val maxY = (decor.height - toolbarH).coerceAtLeast(0)
        val params = attachment.toolbarParams
        params.x = (params.x - dx.toInt()).coerceIn(0, maxX)
        params.y = (params.y - dy.toInt()).coerceIn(0, maxY)
        runCatching {
            val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.updateViewLayout(attachment.toolbarView, params)
        }
    }

    /**
     * Drag-end — animates X to whichever edge (left or right) is closer.
     * Y stays put. Animation runs on the main thread via [ValueAnimator]
     * since we're updating WindowManager params each frame.
     */
    private fun onToolbarDragEnd(activity: Activity) {
        val attachment = attached[activity] ?: return
        if (!attachment.attachedToWindowManager) return
        val decor = activity.window?.decorView ?: return
        val toolbarW = attachment.toolbarView.width.coerceAtLeast(1)
        val padPx = (20 * activity.resources.displayMetrics.density).toInt()
        val maxX = (decor.width - toolbarW).coerceAtLeast(0)
        val params = attachment.toolbarParams
        // params.x = inset from RIGHT (gravity END). near 0 = right edge,
        // near maxX = left edge.
        val midX = maxX / 2
        val targetX = if (params.x < midX) padPx
        else (maxX - padPx).coerceAtLeast(0)
        if (targetX == params.x) return

        val from = params.x
        val animator = ValueAnimator.ofInt(from, targetX).apply {
            duration = 280L
            interpolator = OvershootInterpolator(0.5f)
            addUpdateListener { v ->
                params.x = v.animatedValue as Int
                runCatching {
                    val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.updateViewLayout(attachment.toolbarView, params)
                }
            }
        }
        animator.start()
    }

    private fun detach(activity: Activity) {
        val attachment = attached.remove(activity) ?: return
        runCatching {
            (attachment.captureView.parent as? ViewGroup)?.removeView(attachment.captureView)
            attachment.captureView.disposeComposition()
        }
        if (attachment.attachedToWindowManager) {
            runCatching {
                val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeViewImmediate(attachment.toolbarView)
            }
            attachment.attachedToWindowManager = false
        }
        runCatching { attachment.toolbarView.disposeComposition() }
    }

    /** Per-activity bundle of state + views + window params. */
    private class AttachedActivity(
        val state: JellyOverlayState,
        val captureView: ComposeView,
        val toolbarView: ComposeView,
        val toolbarParams: WindowManager.LayoutParams,
        var attachedToWindowManager: Boolean = false,
    )
}
