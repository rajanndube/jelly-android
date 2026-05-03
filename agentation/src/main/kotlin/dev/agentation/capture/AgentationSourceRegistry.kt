package dev.agentation.capture

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
val AgentationSourceKey: SemanticsPropertyKey<SourceInfo> =
    SemanticsPropertyKey("AgentationSource")

var SemanticsPropertyReceiver.agentationSource: SourceInfo by AgentationSourceKey

/**
 * Tags the composable with a source location. The compiler plugin injects
 * this call automatically into every `@Composable` function in the host
 * app's source set. Apps can also call it manually:
 *
 * ```
 * @Composable
 * fun MyScreen() {
 *     Column(Modifier.agentationSource("MyScreen.kt", 42)) { ... }
 * }
 * ```
 */
@Stable
fun Modifier.agentationSource(file: String, line: Int): Modifier =
    this.semantics { agentationSource = SourceInfo(file, line) }
