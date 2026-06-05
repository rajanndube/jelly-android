package dev.jelly

import android.app.Application

/**
 * Static entry point for the **architecture-agnostic** integration
 * (recommended for v0.6+).
 *
 * Call once from `Application.onCreate`:
 * ```
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         if (BuildConfig.DEBUG) {
 *             Jelly.install(this)
 *         }
 *     }
 * }
 * ```
 *
 * From that point, every activity in the app gets the toolbar overlay
 * automatically — no per-screen `Jelly { }` wrapping, no per-fragment
 * wiring, no `Modifier.testTag` plumbing. Hit-tests run against every
 * Compose root in the activity's decor view, so the overlay works with
 * apps that mix Compose with View-based UI, host multiple `setContent`
 * calls, or use dialogs/fragments.
 *
 * Use [uninstall] to remove the overlay (e.g. when the user disables QA
 * mode at runtime).
 */
object Jelly {
    @Volatile
    private var controller: ActivityOverlayController? = null

    /**
     * Installs the overlay across every activity owned by [application].
     * Idempotent — calling twice is safe; the second call is ignored.
     */
    @JvmStatic
    @JvmOverloads
    fun install(
        application: Application,
        config: JellyConfig = JellyConfig(),
    ) {
        if (controller != null) return
        // Trim the screenshot cache once per process start so it can't grow
        // unbounded across QA sessions. Cheap; runs on the main thread but
        // only touches a single directory under cacheDir.
        dev.jelly.capture.JellyCacheCleanup.trim(application)
        val ctrl = ActivityOverlayController(config)
        application.registerActivityLifecycleCallbacks(ctrl)
        controller = ctrl
    }

    /**
     * Removes the overlay from every activity and stops listening for
     * future activity creations. Safe to call even if [install] was never
     * called.
     */
    @JvmStatic
    fun uninstall(application: Application) {
        val ctrl = controller ?: return
        application.unregisterActivityLifecycleCallbacks(ctrl)
        ctrl.detachAll()
        controller = null
    }

    /** True between [install] and [uninstall] — useful for QA toggles. */
    @JvmStatic
    fun isInstalled(): Boolean = controller != null
}
