package dev.agentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.agentation.output.DetailLevel
import dev.agentation.storage.Settings
import dev.agentation.theme.AccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))

            // Detail level
            SectionLabel("Output detail")
            DetailLevelRow(
                selected = settings.detailLevel,
                onSelect = { level -> onSettingsChange(settings.copy(detailLevel = level)) },
                accent = settings.accentColor.color,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Accent color
            SectionLabel("Accent color")
            AccentRow(settings.accentColor) { accent ->
                onSettingsChange(settings.copy(accentColor = accent))
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sync to MCP server", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Posts each annotation to /sessions/{id}/annotations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.syncEnabled,
                    onCheckedChange = { onSettingsChange(settings.copy(syncEnabled = it)) },
                )
            }

            if (settings.syncEnabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = settings.endpoint.orEmpty(),
                    onValueChange = { onSettingsChange(settings.copy(endpoint = it.ifBlank { null })) },
                    label = { Text("Endpoint base URL") },
                    placeholder = { Text("https://mcp.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.webhookUrl.orEmpty(),
                    onValueChange = { onSettingsChange(settings.copy(webhookUrl = it.ifBlank { null })) },
                    label = { Text("Webhook URL (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DetailLevelRow(
    selected: DetailLevel,
    onSelect: (DetailLevel) -> Unit,
    accent: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailLevel.values().forEach { level ->
            val isSelected = level == selected
            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val container = if (isSelected) accent.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent
            val border = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant
            val text = if (isSelected) accent else MaterialTheme.colorScheme.onSurface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .pressScale(interactionSource, pressedScale = 0.97f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(container)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = border,
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelect(level) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(level.name, color = text, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AccentRow(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccentColor.values().forEach { accent ->
            val isSelected = accent == selected
            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            // Selected swatch wears a slim outline ring (the swatch's own color, dimmed)
            // — far cleaner than a stacked white+onSurface ring.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .pressScale(interactionSource, pressedScale = 0.9f),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(2.dp, accent.color, CircleShape),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 26.dp else 32.dp)
                        .background(accent.color, CircleShape)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelect(accent) },
                        ),
                )
            }
        }
    }
}
