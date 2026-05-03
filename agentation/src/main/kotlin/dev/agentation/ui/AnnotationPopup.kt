package dev.agentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.agentation.capture.CapturedElement
import dev.agentation.model.AnnotationIntent
import dev.agentation.model.AnnotationSeverity

data class PopupSubmission(
    val comment: String,
    val intent: AnnotationIntent?,
    val severity: AnnotationSeverity?,
)

@Composable
fun AnnotationPopup(
    captured: CapturedElement,
    accent: Color,
    onSubmit: (PopupSubmission) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var comment by remember { mutableStateOf("") }
    var intent by remember { mutableStateOf<AnnotationIntent?>(null) }
    var severity by remember { mutableStateOf<AnnotationSeverity?>(null) }

    Popup(
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true),
    ) {
        Surface(
            modifier = modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Text(
                    text = captured.displayName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
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

                Spacer(Modifier.height(12.dp))

                // Comment textarea
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("What should change?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .border(1.dp, accent, RoundedCornerShape(8.dp))
                        .background(Color.Transparent, RoundedCornerShape(8.dp)),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default,
                    ),
                )

                Spacer(Modifier.height(12.dp))

                // Intent chips
                Text(
                    text = "Intent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnnotationIntent.values().forEach { value ->
                        IntentChip(
                            label = value.label(),
                            selected = intent == value,
                            accent = accent,
                            onClick = { intent = if (intent == value) null else value },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Severity chips
                Text(
                    text = "Severity",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnnotationSeverity.values().forEach { value ->
                        IntentChip(
                            label = value.label(),
                            selected = severity == value,
                            accent = accent,
                            onClick = { severity = if (severity == value) null else value },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            if (comment.isNotBlank()) {
                                onSubmit(PopupSubmission(comment.trim(), intent, severity))
                            }
                        },
                        enabled = comment.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
private fun IntentChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val border = if (selected) accent else MaterialTheme.colorScheme.outlineVariant
    val text = if (selected) accent else MaterialTheme.colorScheme.onSurface
    AssistChip(
        onClick = onClick,
        label = { Text(label, color = text) },
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = border,
            borderWidth = if (selected) 2.dp else 1.dp,
        ),
    )
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
