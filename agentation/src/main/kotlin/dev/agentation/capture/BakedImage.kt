package dev.agentation.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color as AColor
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.StyleSpan
import dev.agentation.model.BoundingBox
import java.io.File
import java.io.FileOutputStream

/**
 * Bakes the annotation overlay into a self-contained image:
 *  - Stroke rectangle on the selected element's bounds
 *  - Caption strip below the screenshot with structured metadata —
 *    Element name (bold) + Location: + Source: (if any) + Feedback:
 *
 * Why bake instead of overlay-at-render-time:
 *   1. Apps that consume the share intent (Slack, WhatsApp, …) drop
 *      `EXTRA_TEXT` when an image is attached. The image is the ONLY thing
 *      that's guaranteed to reach the receiver — so the metadata has to live
 *      on the image too, not just in the text.
 *   2. The stroke rectangle drawn as a Compose Canvas overlay is invisible to
 *      the file — anything we share has to be in the bitmap itself.
 *
 * Caption uses SpannableStringBuilder with bold StyleSpans on the labels
 * (`Location:`, `Source:`, `Feedback:`) so they read like proper headings
 * rather than plain text.
 */
object BakedImage {

    fun bake(
        rawPath: String,
        bounds: BoundingBox?,
        accentArgb: Int,
        elementName: String,
        elementPath: String,
        sourceFile: String?,
        comment: String,
        intent: String? = null,
        severity: String? = null,
        outFile: File,
        densityScale: Float,
    ): String? {
        val raw = runCatching { BitmapFactory.decodeFile(rawPath) }.getOrNull() ?: return null
        try {
            val width = raw.width
            val captionPaddingPx = (16f * densityScale).toInt()
            val bodyTextSize = 14f * densityScale
            val titleTextSize = 16f * densityScale
            val maxCaptionWidth = (width - captionPaddingPx * 2).coerceAtLeast(1)

            val bodyPaint = TextPaint().apply {
                color = AColor.WHITE
                textSize = bodyTextSize
                isAntiAlias = true
            }
            val titlePaint = TextPaint().apply {
                color = AColor.WHITE
                textSize = titleTextSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val tagPaint = TextPaint().apply {
                color = AColor.argb(255, 161, 161, 170) // zinc-400
                textSize = bodyTextSize * 0.9f
                isAntiAlias = true
            }

            // ── Title (element name, bold, slightly larger) ────────────────
            val titleLayout = StaticLayout.Builder
                .obtain(elementName, 0, elementName.length, titlePaint, maxCaptionWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

            // ── Body (Location / Source / Feedback with bold labels) ───────
            val body = SpannableStringBuilder().apply {
                appendBoldLabel("Location: ")
                append(elementPath)
                if (!sourceFile.isNullOrBlank()) {
                    append('\n')
                    appendBoldLabel("Source: ")
                    append(sourceFile)
                }
                append('\n')
                appendBoldLabel("Feedback: ")
                append(comment)
            }
            val bodyLayout = StaticLayout.Builder
                .obtain(body, 0, body.length, bodyPaint, maxCaptionWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.15f)
                .setMaxLines(MAX_BODY_LINES)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

            // ── Tag line (intent · severity) ───────────────────────────────
            val tagText = listOfNotNull(
                intent?.takeIf { it.isNotBlank() }?.let { "intent: $it" },
                severity?.takeIf { it.isNotBlank() }?.let { "severity: $it" },
            ).joinToString(" · ")
            val tagLayout = if (tagText.isNotEmpty()) {
                StaticLayout.Builder
                    .obtain(tagText, 0, tagText.length, tagPaint, maxCaptionWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(1)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
            } else null

            val gapPx = (10f * densityScale).toInt()
            val tagGapPx = (8f * densityScale).toInt()
            val captionStripHeight = captionPaddingPx +
                titleLayout.height + gapPx +
                bodyLayout.height +
                (tagLayout?.let { tagGapPx + it.height } ?: 0) +
                captionPaddingPx

            val outHeight = raw.height + captionStripHeight
            val out = Bitmap.createBitmap(width, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)

            // 1. Screenshot
            canvas.drawBitmap(raw, 0f, 0f, null)

            // 2. Stroke rectangle on the selected element's bounds
            bounds?.let { box ->
                val strokeWidthPx = (3f * densityScale).coerceAtLeast(2f)
                val strokePaint = Paint().apply {
                    color = accentArgb
                    style = Paint.Style.STROKE
                    strokeWidth = strokeWidthPx
                    isAntiAlias = true
                }
                val half = strokeWidthPx / 2f
                canvas.drawRect(
                    box.x + half,
                    box.y + half,
                    box.x + box.width - half,
                    box.y + box.height - half,
                    strokePaint,
                )
            }

            // 3. Caption strip
            val stripTop = raw.height.toFloat()
            val stripPaint = Paint().apply { color = AColor.argb(245, 18, 18, 22) } // near zinc-950
            canvas.drawRect(0f, stripTop, width.toFloat(), stripTop + captionStripHeight, stripPaint)

            // Title
            canvas.save()
            canvas.translate(captionPaddingPx.toFloat(), stripTop + captionPaddingPx)
            titleLayout.draw(canvas)
            canvas.restore()

            // Body
            canvas.save()
            canvas.translate(
                captionPaddingPx.toFloat(),
                stripTop + captionPaddingPx + titleLayout.height + gapPx,
            )
            bodyLayout.draw(canvas)
            canvas.restore()

            // Tags
            tagLayout?.let { tags ->
                canvas.save()
                canvas.translate(
                    captionPaddingPx.toFloat(),
                    stripTop + captionPaddingPx + titleLayout.height + gapPx +
                        bodyLayout.height + tagGapPx,
                )
                tags.draw(canvas)
                canvas.restore()
            }

            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { stream ->
                @Suppress("DEPRECATION")
                out.compress(Bitmap.CompressFormat.WEBP, 85, stream)
            }
            out.recycle()
            return outFile.absolutePath
        } finally {
            raw.recycle()
        }
    }

    private fun SpannableStringBuilder.appendBoldLabel(text: String) {
        val start = length
        append(text)
        setSpan(StyleSpan(Typeface.BOLD), start, length, 0)
    }

    private const val MAX_BODY_LINES = 8
}
