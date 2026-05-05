package dev.jelly.sample

import android.app.Application
import dev.jelly.Jelly
import dev.jelly.JellyConfig

/**
 * Demonstrates the **Application-level install** pattern. One call here gets
 * the toolbar overlaying every activity in the app — no per-screen wrapping
 * required.
 *
 * In a production host you'd typically gate this with `BuildConfig.DEBUG` or
 * a QA-only build flavor so the overlay never ships to release.
 */
class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Jelly.install(
            application = this,
            config = JellyConfig(),
        )
    }
}
