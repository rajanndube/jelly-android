package dev.agentation.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import dev.agentation.theme.AgentationMotion

/**
 * Adds a subtle scale-down on press so any tappable element responds to touch
 * the way buttons do in iOS / well-designed Android apps. The press uses a
 * slightly slower curve (160ms) than the release (120ms) — slow where the user
 * is deciding, fast where the system is responding.
 *
 * Pair with `Modifier.clickable(interactionSource = ...)` so the source is
 * shared and the press state stays in sync with the click handler.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = if (pressed) AgentationMotion.pressDown() else AgentationMotion.pressUp(),
        label = "agentation.pressScale",
    )
    return this.scale(scale)
}

/**
 * Convenience — creates and remembers a fresh InteractionSource and applies
 * pressScale. Use when you don't need to share the source with another modifier.
 */
@Composable
fun Modifier.pressScale(pressedScale: Float = 0.97f): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.pressScale(interactionSource, pressedScale)
}
