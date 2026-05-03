package dev.agentation.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.ui.geometry.Rect as ComposeRect
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min

/**
 * Captures a region of the host Window as a WEBP_LOSSY file in cacheDir.
 *
 * Returns the absolute file path on success, null otherwise. Falls back to
 * `View.draw(Canvas)` on devices/contexts where PixelCopy isn't available.
 */
object Screenshot {

    suspend fun captureRegion(
        context: Context,
        window: Window?,
        rootView: View,
        regionInWindow: ComposeRect,
        annotationId: String,
    ): String? {
        val src = clamp(regionInWindow, rootView)
        if (src.width() <= 0 || src.height() <= 0) return null

        val bitmap = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888)

        val ok = if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pixelCopy(window, src, bitmap)
        } else {
            drawFallback(rootView, src, bitmap)
        }

        if (!ok) {
            bitmap.recycle()
            return null
        }

        val outDir = File(context.cacheDir, "agentation").apply { mkdirs() }
        val outFile = File(outDir, "$annotationId.webp")
        return runCatching {
            FileOutputStream(outFile).use { stream ->
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 80, stream)
            }
            outFile.absolutePath
        }.also {
            bitmap.recycle()
        }.getOrNull()
    }

    private fun clamp(rect: ComposeRect, view: View): Rect {
        val l = max(0, rect.left.toInt())
        val t = max(0, rect.top.toInt())
        val r = min(view.width, rect.right.toInt())
        val b = min(view.height, rect.bottom.toInt())
        return Rect(l, t, r, b)
    }

    private suspend fun pixelCopy(window: Window, src: Rect, dest: Bitmap): Boolean =
        suspendCoroutine { cont ->
            val thread = HandlerThread("agentation-pixelcopy").apply { start() }
            val handler = Handler(thread.looper)
            try {
                PixelCopy.request(window, src, dest, { result ->
                    thread.quitSafely()
                    cont.resume(result == PixelCopy.SUCCESS)
                }, handler)
            } catch (t: Throwable) {
                thread.quitSafely()
                cont.resume(false)
            }
        }

    private fun drawFallback(view: View, src: Rect, dest: Bitmap): Boolean = runCatching {
        val canvas = android.graphics.Canvas(dest)
        canvas.translate(-src.left.toFloat(), -src.top.toFloat())
        view.draw(canvas)
        true
    }.getOrDefault(false)
}
