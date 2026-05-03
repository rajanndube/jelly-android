package dev.agentation.output

import dev.agentation.model.Annotation
import kotlin.math.roundToInt

/**
 * 1:1 port of `generateOutput()` from
 * package/src/utils/generate-output.ts:27-129.
 *
 * The output format is the contract that downstream AI agents read, so byte-for-byte
 * parity matters where the field exists on Android. Web-only fields (cssClasses,
 * computedStyles, fullPath) are simply absent on Android annotations and the
 * corresponding lines are skipped — matching how the web version skips empty fields.
 *
 * On Android, the "React:" line is repurposed for the Compose function hierarchy
 * (sourced from ui-tooling-data in debug builds) so existing agent prompts that
 * look for **React:** still find the equivalent metadata.
 */
class OutputGenerator(
    private val viewportWidth: Int,
    private val viewportHeight: Int,
    private val nowIso: () -> String = { java.time.Instant.now().toString() },
    private val deviceInfo: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}; Android ${android.os.Build.VERSION.RELEASE}",
    private val devicePixelRatio: Float = 1f,
) {
    fun generate(
        annotations: List<Annotation>,
        screenKey: String,
        detailLevel: DetailLevel = DetailLevel.Standard,
    ): String {
        if (annotations.isEmpty()) return ""

        val viewport = "${viewportWidth}×${viewportHeight}"
        val sb = StringBuilder()
        sb.append("## Page Feedback: ").append(screenKey).append("\n")

        when (detailLevel) {
            DetailLevel.Forensic -> {
                sb.append("\n**Environment:**\n")
                sb.append("- Viewport: ").append(viewport).append("\n")
                sb.append("- Screen: ").append(screenKey).append("\n")
                sb.append("- Device: ").append(deviceInfo).append("\n")
                sb.append("- Timestamp: ").append(nowIso()).append("\n")
                sb.append("- Device Pixel Ratio: ").append(devicePixelRatio).append("\n")
                sb.append("\n---\n")
            }
            DetailLevel.Compact -> Unit
            else -> sb.append("**Viewport:** ").append(viewport).append("\n")
        }
        sb.append("\n")

        annotations.forEachIndexed { i, a ->
            when (detailLevel) {
                DetailLevel.Compact -> {
                    sb.append(i + 1).append(". **").append(a.element).append("**")
                    a.sourceFile?.let { sb.append(" (").append(it).append(")") }
                    sb.append(": ").append(a.comment)
                    a.selectedText?.let {
                        sb.append(" (re: \"")
                            .append(it.take(30))
                            .append(if (it.length > 30) "..." else "")
                            .append("\")")
                    }
                    sb.append("\n")
                }

                DetailLevel.Forensic -> {
                    sb.append("### ").append(i + 1).append(". ").append(a.element).append("\n")
                    if (a.isMultiSelect == true && a.fullPath != null) {
                        sb.append("*Forensic data shown for first element of selection*\n")
                    }
                    a.fullPath?.let { sb.append("**Full Path:** ").append(it).append("\n") }
                    a.cssClasses?.let { sb.append("**Classes:** ").append(it).append("\n") }
                    a.boundingBox?.let {
                        sb.append("**Position:** x:").append(it.x.roundToInt())
                            .append(", y:").append(it.y.roundToInt())
                            .append(" (").append(it.width.roundToInt())
                            .append("×").append(it.height.roundToInt())
                            .append("px)\n")
                    }
                    sb.append("**Annotation at:** ")
                        .append(String.format("%.1f", a.x))
                        .append("% from left, ")
                        .append(a.y.roundToInt())
                        .append("px from top\n")
                    a.selectedText?.let { sb.append("**Selected text:** \"").append(it).append("\"\n") }
                    if (a.nearbyText != null && a.selectedText == null) {
                        sb.append("**Context:** ").append(a.nearbyText.take(100)).append("\n")
                    }
                    a.computedStyles?.let { sb.append("**Computed Styles:** ").append(it).append("\n") }
                    a.accessibility?.let { sb.append("**Accessibility:** ").append(it).append("\n") }
                    a.nearbyElements?.let { sb.append("**Nearby Elements:** ").append(it).append("\n") }
                    a.sourceFile?.let { sb.append("**Source:** ").append(it).append("\n") }
                    a.composableHierarchy?.let { sb.append("**Composables:** ").append(it).append("\n") }
                    a.screenshotPath?.let { sb.append("**Screenshot:** ").append(it).append("\n") }
                    sb.append("**Feedback:** ").append(a.comment).append("\n\n")
                }

                DetailLevel.Standard, DetailLevel.Detailed -> {
                    sb.append("### ").append(i + 1).append(". ").append(a.element).append("\n")
                    sb.append("**Location:** ").append(a.elementPath).append("\n")
                    a.sourceFile?.let { sb.append("**Source:** ").append(it).append("\n") }
                    a.composableHierarchy?.let { sb.append("**Composables:** ").append(it).append("\n") }
                    if (detailLevel == DetailLevel.Detailed) {
                        a.cssClasses?.let { sb.append("**Classes:** ").append(it).append("\n") }
                        a.boundingBox?.let {
                            sb.append("**Position:** ").append(it.x.roundToInt())
                                .append("px, ").append(it.y.roundToInt())
                                .append("px (").append(it.width.roundToInt())
                                .append("×").append(it.height.roundToInt())
                                .append("px)\n")
                        }
                    }
                    a.selectedText?.let { sb.append("**Selected text:** \"").append(it).append("\"\n") }
                    if (detailLevel == DetailLevel.Detailed && a.nearbyText != null && a.selectedText == null) {
                        sb.append("**Context:** ").append(a.nearbyText.take(100)).append("\n")
                    }
                    a.screenshotPath?.let { sb.append("**Screenshot:** ").append(it).append("\n") }
                    sb.append("**Feedback:** ").append(a.comment).append("\n\n")
                }
            }
        }

        return sb.toString().trim()
    }
}
