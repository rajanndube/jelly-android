package dev.jelly.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Live preview overlay drawn while the user holds their finger down in
 * annotate mode. A soft accent-tinted fill plus a stroke rectangle traces the
 * currently-hovered element's bounds; a small label chip near the rectangle
 * shows the element's name.
 */
@Composable
fun LiveHoverOverlay(
    bounds: Rect,
    label: String,
    accent: Color,
) {
    val density = LocalDensity.current
    val strokePx = with(density) { 2.5.dp.toPx() }
    val cornerPx = with(density) { 6.dp.toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val half = strokePx / 2f
        val w = (bounds.width - strokePx).coerceAtLeast(0f)
        val h = (bounds.height - strokePx).coerceAtLeast(0f)
        // Soft tinted fill — communicates "this region is selected" without
        // overwhelming the host content underneath.
        drawRoundRect(
            color = accent.copy(alpha = 0.12f),
            topLeft = Offset(bounds.left, bounds.top),
            size = Size(bounds.width, bounds.height),
            cornerRadius = CornerRadius(cornerPx, cornerPx),
        )
        // Crisp accent stroke on the boundary.
        drawRoundRect(
            color = accent,
            topLeft = Offset(bounds.left + half, bounds.top + half),
            size = Size(w, h),
            cornerRadius = CornerRadius(cornerPx, cornerPx),
            style = Stroke(width = strokePx),
        )
    }

    // Label chip — sits above the bounds; flips below if too close to the top.
    val labelPaddingPx = with(density) { 8.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }
    val approxLabelHeightPx = with(density) { 26.dp.toPx() }
    Box(
        modifier = Modifier
            .offset {
                val drawBelow = bounds.top < approxLabelHeightPx + gapPx + 16f
                val y = if (drawBelow) bounds.bottom + gapPx
                else bounds.top - gapPx - approxLabelHeightPx
                IntOffset(
                    x = (bounds.left + labelPaddingPx).roundToInt(),
                    y = y.roundToInt().coerceAtLeast(0),
                )
            }
            .background(accent, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
