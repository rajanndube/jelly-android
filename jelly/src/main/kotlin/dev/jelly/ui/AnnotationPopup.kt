package dev.jelly.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.jelly.capture.CapturedElement
import dev.jelly.model.AnnotationIntent
import dev.jelly.model.AnnotationSeverity
import dev.jelly.model.BoundingBox
import dev.jelly.theme.JellyMotion

data class PopupSubmission(
    val comment: String,
    val intent: AnnotationIntent?,
    val severity: AnnotationSeverity?,
)

@Composable
fun AnnotationPopup(
    captured: CapturedElement,
    screenshotPath: String?,
    accent: Color,
    onSubmit: (PopupSubmission) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var comment by remember { mutableStateOf("") }
    var intent by remember { mutableStateOf<AnnotationIntent?>(null) }
    var severity by remember { mutableStateOf<AnnotationSeverity?>(null) }
    val bounds = remember(captured.bounds) {
        BoundingBox(
            x = captured.bounds.left,
            y = captured.bounds.top,
            width = captured.bounds.width,
            height = captured.bounds.height,
        )
    }

    // Animated entrance — scale 0.96 → 1.0 + opacity 0 → 1 with strong ease-out.
    // Starts at 0.96 (not 0) so the popup feels like it materializes rather than
    // erupting from nothing.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.96f,
        animationSpec = tween(JellyMotion.PopupMs, easing = JellyMotion.EaseOut),
        label = "popupScale",
    )
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(JellyMotion.ChipMs, easing = JellyMotion.EaseOut),
        label = "popupAlpha",
    )

    // Cap the popup at 85% of screen height so it can't push the action row
    // off-screen when the captured element name / path / source is long. The
    // body content scrolls inside; the actions stay sticky at the bottom.
    val configuration = LocalConfiguration.current
    val maxPopupHeight = (configuration.screenHeightDp * 0.85f).dp
    val bodyScrollState = rememberScrollState()

    Popup(
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true),
    ) {
        Surface(
            modifier = modifier
                .widthIn(min = 300.dp, max = 380.dp)
                .heightIn(max = maxPopupHeight)
                .padding(16.dp)
                .scale(scale)
                .alpha(opacity),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Scrollable body ────────────────────────────────────────
                // weight(1f, fill = false) lets this region shrink to its
                // natural size when content fits, but cap-and-scroll when it
                // doesn't. The action row below always stays visible.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(bodyScrollState)
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                ) {
                    // ── Header ──────────────────────────────────────────────
                    Text(
                        text = captured.displayName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = captured.elementPath,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    captured.sourceFile?.let { source ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = source,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }

                    // ── Captured screenshot ────────────────────────────────
                    screenshotPath?.let { path ->
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(12.dp),
                                ),
                        ) {
                            AnnotatedScreenshot(
                                screenshotPath = path,
                                bounds = bounds,
                                accent = accent,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 240.dp),
                            )
                        }
                    }

                    // ── Comment field ──────────────────────────────────────
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it },
                        placeholder = {
                            Text(
                                "What should change?",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(108.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = accent,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Default,
                        ),
                    )

                    // ── Tag chips ──────────────────────────────────────────
                    Spacer(Modifier.height(14.dp))
                    ChipSection(label = "Intent") {
                        AnnotationIntent.values().forEach { value ->
                            JellyChip(
                                label = value.label(),
                                selected = intent == value,
                                accent = accent,
                                onClick = { intent = if (intent == value) null else value },
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    ChipSection(label = "Severity") {
                        AnnotationSeverity.values().forEach { value ->
                            JellyChip(
                                label = value.label(),
                                selected = severity == value,
                                accent = accent,
                                onClick = { severity = if (severity == value) null else value },
                            )
                        }
                    }
                }

                // ── Sticky action row ──────────────────────────────────────
                // Subtle divider so it reads as a separate region when the
                // body above is scrollable (and the user can see content
                // continues above the actions).
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    thickness = 1.dp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(4.dp))
                    PrimaryButton(
                        label = "Add",
                        accent = accent,
                        enabled = comment.isNotBlank(),
                        onClick = {
                            if (comment.isNotBlank()) {
                                onSubmit(PopupSubmission(comment.trim(), intent, severity))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipSection(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

@Composable
internal fun JellyChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor = if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (selected) accent else MaterialTheme.colorScheme.outline
    val textColor = if (selected) accent else MaterialTheme.colorScheme.onSurface
    val borderWidth = if (selected) 1.5.dp else 1.dp

    Box(
        modifier = Modifier
            .pressScale(interactionSource, pressedScale = 0.95f)
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .border(borderWidth, borderColor, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun PrimaryButton(label: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val container = if (enabled) accent else MaterialTheme.colorScheme.surfaceVariant
    val labelColor = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .pressScale(interactionSource, pressedScale = 0.96f)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = labelColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun AnnotationIntent.label(): String = when (this) {
    AnnotationIntent.Fix -> "Fix"
    AnnotationIntent.Change -> "Change"
    AnnotationIntent.Question -> "Question"
    AnnotationIntent.Approve -> "Approve"
}

private fun AnnotationSeverity.label(): String = when (this) {
    AnnotationSeverity.Blocking -> "Blocking"
    AnnotationSeverity.Important -> "Important"
    AnnotationSeverity.Suggestion -> "Suggestion"
}
