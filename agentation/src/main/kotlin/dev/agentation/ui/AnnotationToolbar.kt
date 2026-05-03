package dev.agentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Floating toolbar — collapsed FAB-with-badge that expands to action buttons.
 * Equivalent of `page-toolbar-css` from the web version, simplified for v0.1.
 */
@Composable
fun AnnotationToolbar(
    annotateMode: Boolean,
    annotationCount: Int,
    accent: Color,
    onToggleAnnotate: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
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

            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(4.dp))
                    ToolButton(Icons.Default.ContentCopy, "Copy", onClick = onCopy)
                    ToolButton(Icons.Default.Share, "Share", onClick = onShare)
                    ToolButton(Icons.Default.DeleteOutline, "Clear", onClick = onClear)
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

    BadgedBox(
        badge = {
            if (count > 0) {
                Badge(containerColor = accent, contentColor = Color.White) {
                    Text(count.toString())
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(container, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Toggle annotate mode",
                tint = tint,
            )
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}
