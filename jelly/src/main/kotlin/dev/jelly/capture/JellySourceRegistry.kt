package dev.jelly.capture

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/**
 * Source location attached to a composable, surfaced at hit-test time.
 */
data class SourceInfo(val file: String, val line: Int) {
    fun formatted(): String = "$file:$line"
}

/**
 * Semantics property key — source info travels through Compose's semantics
 * tree the same way `testTag` and `contentDescription` do, so it's readable
 * from any `SemanticsNode` and survives the merged/unmerged distinction.
 */
val JellySourceKey: SemanticsPropertyKey<SourceInfo> =
    SemanticsPropertyKey("JellySource")

var SemanticsPropertyReceiver.jellySource: SourceInfo by JellySourceKey

/**
 * Tags a composable with a source location. Most apps don't need to call
 * this — the SDK automatically infers the host activity's source via stack
 * inspection at first composition (see `detectHostSource()`). Use it
 * manually when you want screen-level precision instead of activity-level:
 *
 * ```
 * @Composable
 * fun LoginScreen() {
 *     Column(Modifier.jellySource("LoginScreen.kt", 42)) { ... }
 * }
 * ```
 *
 * SemanticsCapture walks the hit node's ancestors looking for the closest
 * tag, so tagging the screen root is enough — every annotation inside it
 * inherits.
 */
@Stable
fun Modifier.jellySource(file: String, line: Int): Modifier =
    this.semantics { jellySource = SourceInfo(file, line) }

/**
 * Walks the current thread's stack looking for the first frame outside
 * Jelly, Compose, and Kotlin/Java internals — that's the host app's
 * `setContent { }` site (or wherever `Jelly { }` was wrapped).
 *
 * Used as a fallback Source when no `Modifier.jellySource()` tag is
 * found in the semantics tree. Result: every annotation gets a meaningful
 * `Source: MainActivity.kt:36` automatically, without per-screen plumbing.
 *
 * Returns null when called outside a meaningful host stack (e.g. from a
 * background thread) or when filenames are stripped by R8 — caller should
 * gracefully fall back to no source line in that case.
 */
fun detectHostSource(): SourceInfo? = runCatching {
    Throwable().stackTrace.firstOrNull { frame ->
        val cls = frame.className
        val file = frame.fileName
        file != null &&
            !cls.startsWith("dev.jelly.") &&
            !cls.startsWith("androidx.compose.") &&
            !cls.startsWith("androidx.activity.") &&
            !cls.startsWith("androidx.lifecycle.") &&
            !cls.startsWith("kotlin.") &&
            !cls.startsWith("kotlinx.") &&
            !cls.startsWith("java.") &&
            !cls.startsWith("jdk.") &&
            !cls.startsWith("sun.")
    }?.let { SourceInfo(it.fileName!!, it.lineNumber.coerceAtLeast(0)) }
}.getOrNull()
