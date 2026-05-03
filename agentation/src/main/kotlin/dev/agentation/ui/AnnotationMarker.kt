package dev.agentation.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agentation.theme.AgentationMotion

/**
 * Numbered dot drawn at a saved annotation position.
 *
 * A pulsing halo behind the dot draws the eye to it without crossing into
 * "look at me" territory — slow (1.4s cycle), low contrast (16% accent at
 * peak), subtle scale variance (1.0 → 1.4). Calm and noticeable.
 */
@Composable
fun AnnotationMarker(
    number: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "markerPulse")
    val haloScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = AgentationMotion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloScale",
    )
    val haloAlpha by infinite.animateFloat(
        initialValue = 0.16f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = AgentationMotion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "haloAlpha",
    )

    Box(
        modifier = modifier.size(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .scale(haloScale)
                .background(accent.copy(alpha = haloAlpha), CircleShape),
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(accent, CircleShape)
                // White ring contrasts against any host background AND against
                // the accent dot — visible whether host theme is light or dark.
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (number > 99) "99+" else number.toString(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
