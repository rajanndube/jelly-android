package dev.jelly.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jelly.model.BoundingBox

/**
 * Renders a full-window screenshot with a stroke rectangle drawn at the
 * selected element's bounds. The bounds are scaled to fit the rendered image's
 * actual size on screen (since `ContentScale.Fit` may letterbox).
 */
@Composable
fun AnnotatedScreenshot(
    screenshotPath: String,
    bounds: BoundingBox?,
    accent: Color,
    modifier: Modifier = Modifier,
    strokeDp: Dp = 3.dp,
) {
    val bitmap = remember(screenshotPath) {
        runCatching { BitmapFactory.decodeFile(screenshotPath) }.getOrNull()
    } ?: return

    Box(modifier = modifier) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Annotation screenshot",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        bounds?.let { box ->
            val srcW = bitmap.width.toFloat()
            val srcH = bitmap.height.toFloat()
            Canvas(modifier = Modifier.fillMaxSize()) {
                // ContentScale.Fit sizes the bitmap so it fits within the box,
                // preserving aspect ratio and letterboxing on the cross-axis.
                val canvasW = size.width
                val canvasH = size.height
                val scale = minOf(canvasW / srcW, canvasH / srcH)
                val drawnW = srcW * scale
                val drawnH = srcH * scale
                val offsetX = (canvasW - drawnW) / 2f
                val offsetY = (canvasH - drawnH) / 2f

                drawRect(
                    color = accent,
                    topLeft = Offset(offsetX + box.x * scale, offsetY + box.y * scale),
                    size = Size(box.width * scale, box.height * scale),
                    style = Stroke(width = strokeDp.toPx()),
                )
            }
        }
    }
}
