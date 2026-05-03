package dev.agentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.agentation.theme.AgentationMotion

/**
 * Floating toolbar — pin (annotate toggle), list (review screen), gear,
 * close. Designed to feel like a single living surface that morphs in width
 * when revealing actions, with subtle press feedback on every button.
 *
 * Key motion choices:
 *  - Surface width animates with `animateContentSize` so the morph is one
 *    coherent gesture rather than a fade reveal.
 *  - Action buttons enter with `scaleIn` from the trigger origin (right edge),
 *    not from their own center, so they feel like they're being pulled out
 *    of the pin chip.
 *  - Press feedback on every button — `pressScale(0.94f)` makes each tap
 *    feel like the surface heard you.
 */
@Composable
fun AnnotationToolbar(
    annotateMode: Boolean,
    annotationCount: Int,
    accent: Color,
    onToggleAnnotate: () -> Unit,
    onOpenReview: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.animateContentSize(
            animationSpec = tween(
                durationMillis = AgentationMotion.PopupMs,
                easing = AgentationMotion.EaseOut,
            ),
        ),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface,
        // Soft shadow — on dark theme the dark surface against the host
        // already provides enough separation; we just want a hint of lift.
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(6.dp),
        ) {
            AnnotateChip(
                annotateMode = annotateMode,
                count = annotationCount,
                accent = accent,
                onClick = {
                    if (annotateMode || expanded) onToggleAnnotate() else expanded = true
                },
            )

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = AgentationMotion.ChipMs,
                        easing = AgentationMotion.EaseOut,
                    ),
                ) + scaleIn(
                    initialScale = 0.85f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                    animationSpec = tween(
                        durationMillis = AgentationMotion.PopupMs,
                        easing = AgentationMotion.EaseOut,
                    ),
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = AgentationMotion.PressUpMs,
                        easing = AgentationMotion.EaseOut,
                    ),
                ) + scaleOut(
                    targetScale = 0.85f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(4.dp))
                    ToolButton(Icons.AutoMirrored.Filled.List, "Review", onClick = onOpenReview)
                    ToolButton(Icons.Default.Settings, "Settings", onClick = onSettings)
                    ToolButton(Icons.Default.Close, "Close") {
                        expanded = false
                        onClose()
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotateChip(
    annotateMode: Boolean,
    count: Int,
    accent: Color,
    onClick: () -> Unit,
) {
    val container = if (annotateMode) accent else MaterialTheme.colorScheme.surfaceVariant
    val tint = if (annotateMode) Color.White else MaterialTheme.colorScheme.onSurface
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(44.dp)
            .pressScale(interactionSource, pressedScale = 0.92f)
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = "Toggle annotate mode",
            tint = tint,
        )
        if (count > 0) {
            // Notch-style badge — sits on the chip's top-right corner without
            // crowding the icon. More elegant than Material's BadgedBox.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(2.dp)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > 9) "9+" else count.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                )
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .pressScale(interactionSource, pressedScale = 0.9f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = LocalContentColor.current,
        )
    }
}

