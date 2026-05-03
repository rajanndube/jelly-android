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
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Captures the host Window as a WEBP_LOSSY file in cacheDir.
 *
 * Returns the absolute file path on success, null otherwise. Falls back to
 * `View.draw(Canvas)` on devices/contexts where PixelCopy isn't available.
 *
 * The default capture is full-window so the review screen can show context
 * around the selected element (drawn as a stroke rectangle on top), instead
 * of just the cropped region. WEBP at ~80% quality keeps storage at
 * ~100-200KB per annotation.
 */
object Screenshot {

    /**
     * Captures the Compose root view's region (not the full window). The
     * resulting bitmap shares the same coordinate space as `boundsInRoot`
     * and `pointerInput` positions, so a stroke rectangle drawn at those
     * bounds aligns precisely on top of the captured element.
     */
    suspend fun captureFullWindow(
        context: Context,
        window: Window?,
        rootView: View,
        annotationId: String,
    ): String? {
        val viewLocation = IntArray(2).also { rootView.getLocationInWindow(it) }
        return captureRegion(
            context = context,
            window = window,
            rootView = rootView,
            srcInWindow = Rect(
                viewLocation[0],
                viewLocation[1],
                viewLocation[0] + rootView.width,
                viewLocation[1] + rootView.height,
            ),
            annotationId = annotationId,
        )
    }

    private suspend fun captureRegion(
        context: Context,
        window: Window?,
        rootView: View,
        srcInWindow: Rect,
        annotationId: String,
    ): String? {
        val w = (srcInWindow.right - srcInWindow.left).coerceAtLeast(0)
        val h = (srcInWindow.bottom - srcInWindow.top).coerceAtLeast(0)
        if (w <= 0 || h <= 0) return null

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val ok = if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pixelCopy(window, srcInWindow, bitmap)
        } else {
            drawFallback(rootView, bitmap)
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

    private fun drawFallback(view: View, dest: Bitmap): Boolean = runCatching {
        val canvas = android.graphics.Canvas(dest)
        view.draw(canvas)
        true
    }.getOrDefault(false)
}
