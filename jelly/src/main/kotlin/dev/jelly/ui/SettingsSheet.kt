package dev.jelly.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.jelly.output.DetailLevel
import dev.jelly.storage.Settings
import dev.jelly.theme.AccentColor

/**
 * Status surface for the manual-sync button rendered inside the settings
 * sheet. The owning composable computes the count and runs the push; the sheet
 * just renders the state.
 *
 * @param storedCount total number of annotations on the device across all
 *   screen keys. The button pushes every one of them regardless of
 *   `syncedTo` — the server idempotently dedupes by id, and re-pushing is
 *   the only way to repopulate a freshly-refreshed jelly-local-sync page.
 * @param isPushing true while a push is in-flight; the button shows progress
 *   and is disabled.
 * @param lastResult short user-facing result string ("Pushed 5 of 5", "1 failed",
 *   ...) from the most recent push, or null if none has run yet this session.
 */
data class CatchUpSyncStatus(
    val storedCount: Int = 0,
    val isPushing: Boolean = false,
    val lastResult: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onDismiss: () -> Unit,
    catchUpStatus: CatchUpSyncStatus = CatchUpSyncStatus(),
    onPushPending: () -> Unit = {},
    /**
     * Optional QR pairing trigger. When non-null, the endpoint TextField shows
     * a scanner icon that calls this. The caller is expected to launch the
     * camera scanner and, on success, update [settings.endpoint] via
     * [onSettingsChange] — keeping the sheet stateless about the scan flow.
     */
    onScanQr: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                // Settings changes commit through `onSettingsChange` as the user
                // toggles each field, so "Done" really just closes the sheet —
                // but it's the affordance users expect when a modal sheet has
                // form-shaped controls. Filled-tonal styling so it reads as a
                // tappable target on dark surfaces; plain TextButton looked
                // like static caption text.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(settings.accentColor.color.copy(alpha = 0.18f))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Done",
                        color = settings.accentColor.color,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

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
                    trailingIcon = onScanQr?.let { scan ->
                        {
                            IconButton(onClick = scan) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan QR code from jelly-local-sync",
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                CatchUpSyncRow(
                    status = catchUpStatus,
                    enabled = !settings.endpoint.isNullOrBlank(),
                    onPush = onPushPending,
                    accent = settings.accentColor.color,
                )
            }
        }
    }
}

@Composable
private fun CatchUpSyncRow(
    status: CatchUpSyncStatus,
    enabled: Boolean,
    onPush: () -> Unit,
    accent: androidx.compose.ui.graphics.Color,
) {
    SectionLabel("Manual sync")
    Text(
        "Re-pushes every locally-stored annotation to the current endpoint. " +
                "Useful after refreshing the local-sync page (which rotates the token) " +
                "or pairing with a new device.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))

    val canPush = enabled && status.storedCount > 0 && !status.isPushing
    val label = when {
        status.isPushing -> "Pushing…"
        status.storedCount == 0 -> "No annotations yet"
        else -> "Push ${status.storedCount} annotation${if (status.storedCount == 1) "" else "s"}"
    }
    // Filled capsule when actionable, dim outline-only when not. Solid accent
    // fill (not 12% alpha) is what makes the active state actually read as
    // "tap me" on dark surfaces — the soft tint was getting lost.
    val pillShape = RoundedCornerShape(50)
    val container = if (canPush) accent else androidx.compose.ui.graphics.Color.Transparent
    val border = when {
        canPush -> accent
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = when {
        canPush -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pressScale(interactionSource, pressedScale = 0.97f)
            .clip(pillShape)
            .background(container)
            .border(
                width = if (canPush) 0.dp else 1.dp,
                color = border,
                shape = pillShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = canPush,
                onClick = onPush,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }

    status.lastResult?.let { result ->
        Spacer(Modifier.height(8.dp))
        Text(
            result,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (!enabled) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Set an endpoint URL above to enable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
