package dev.agentation.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.agentation.model.Annotation
import dev.agentation.model.AnnotationIntent
import dev.agentation.model.AnnotationSeverity
import dev.agentation.theme.AgentationMotion
import kotlinx.coroutines.delay

/**
 * Pluto-style review screen — every saved annotation rendered as a card with
 * its baked screenshot, location, comment, and per-row actions. Bulk actions
 * (Copy / Share / Clear) live in the top bar.
 *
 * Motion:
 *  - Cards stagger in with a small index-based delay (50ms apart) and rise
 *    8dp while fading from 0 → 1 opacity. Stops at the 8th card so a long
 *    list still finishes quickly.
 *  - Every interactive surface (top-bar action, row, icon button) gets
 *    pressScale feedback.
 *  - Top-bar action buttons enter together (no stagger — they're sibling
 *    actions, not list items).
 */
@Composable
fun AnnotationsScreen(
    annotations: List<Annotation>,
    accent: Color,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit,
    onShareAll: () -> Unit,
    onClearAll: () -> Unit,
    onDeleteOne: (Annotation) -> Unit,
    onShareOne: (Annotation) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    count = annotations.size,
                    accent = accent,
                    onDismiss = onDismiss,
                    onCopyAll = onCopyAll.takeIf { annotations.isNotEmpty() },
                    onShareAll = onShareAll.takeIf { annotations.isNotEmpty() },
                    onClearAll = onClearAll.takeIf { annotations.isNotEmpty() },
                )
                if (annotations.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(annotations, key = { _, a -> a.id }) { index, annotation ->
                            AnnotationCard(
                                index = index,
                                annotation = annotation,
                                accent = accent,
                                onDelete = { onDeleteOne(annotation) },
                                onShare = { onShareOne(annotation) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    count: Int,
    accent: Color,
    onDismiss: () -> Unit,
    onCopyAll: (() -> Unit)?,
    onShareAll: (() -> Unit)?,
    onClearAll: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(icon = Icons.Default.Close, contentDescription = "Close", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Annotations",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (count > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(accent, RoundedCornerShape(999.dp))
                            .padding(horizontal = 9.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = count.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (onCopyAll != null || onShareAll != null || onClearAll != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onCopyAll?.let {
                        ActionPill("Copy", Icons.Default.ContentCopy, accent, isPrimary = false, onClick = it)
                    }
                    onShareAll?.let {
                        ActionPill("Share", Icons.Default.Share, accent, isPrimary = true, onClick = it)
                    }
                    onClearAll?.let {
                        ActionPill("Clear", Icons.Default.DeleteOutline, accent, isPrimary = false, onClick = it)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    icon: ImageVector,
    accent: Color,
    isPrimary: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val container = if (isPrimary) accent else MaterialTheme.colorScheme.surfaceVariant
    val text = if (isPrimary) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .pressScale(interactionSource, pressedScale = 0.96f)
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .pressScale(interactionSource, pressedScale = 0.92f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun AnnotationCard(
    index: Int,
    annotation: Annotation,
    accent: Color,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    // Stagger entrance — small index-based delay, capped at 8 cards so a long
    // scrollback doesn't drag.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(8) * AgentationMotion.StaggerStepMs).toLong())
        visible = true
    }
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AgentationMotion.PopupMs, easing = AgentationMotion.EaseOut),
        label = "cardAlpha",
    )
    val translate by animateFloatAsState(
        targetValue = if (visible) 0f else 12f,
        animationSpec = tween(AgentationMotion.PopupMs, easing = AgentationMotion.EaseOut),
        label = "cardTranslate",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(opacity)
            .graphicsLayer { translationY = translate },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberBadge(number = index + 1, accent = accent)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = annotation.element,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = annotation.elementPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    annotation.sourceFile?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                CircleIconButton(Icons.Default.Share, "Share annotation", onClick = onShare)
                CircleIconButton(Icons.Default.DeleteOutline, "Delete annotation", onClick = onDelete)
            }

            annotation.screenshotPath?.let { path ->
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                ) {
                    BakedThumbnail(path = path)
                }
            }

            // Tag chips — show only when there's something to show.
            val tags = buildList {
                annotation.intent?.let { add(StaticTag(it.label(), accent)) }
                annotation.severity?.let { add(StaticTag(it.label(), accent)) }
                if (annotation.syncedTo != null) add(StaticTag("Synced", accent))
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(tag.color.copy(alpha = 0.12f))
                                .border(1.dp, tag.color.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = tag.label,
                                color = tag.color,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class StaticTag(val label: String, val color: Color)

@Composable
private fun NumberBadge(number: Int, accent: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BakedThumbnail(path: String) {
    val bitmap = remember(path) {
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Annotation screenshot",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No annotations yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap the pin in the toolbar, then long-press any element to capture it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun AnnotationIntent.label(): String = when (this) {
    AnnotationIntent.Fix -> "fix"
    AnnotationIntent.Change -> "change"
    AnnotationIntent.Question -> "question"
    AnnotationIntent.Approve -> "approve"
}

private fun AnnotationSeverity.label(): String = when (this) {
    AnnotationSeverity.Blocking -> "blocking"
    AnnotationSeverity.Important -> "important"
    AnnotationSeverity.Suggestion -> "suggestion"
}
