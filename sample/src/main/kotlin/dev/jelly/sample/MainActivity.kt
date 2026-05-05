package dev.jelly.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import dev.jelly.capture.jellySource

/**
 * The activity contains zero Jelly code — the toolbar comes from
 * [SampleApp.onCreate]'s `Jelly.install(this)` call. The activity just
 * does its normal `setContent { ... }`; the install pattern attaches the
 * overlay automatically to this and every other activity in the app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DemoScreen()
                }
            }
        }
    }
}

@Composable
private fun DemoScreen() {
    var name by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Jelly Sample",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "Tap the location-pin button (bottom-right). Tap the pin to expand. " +
                "Long-press anywhere on this screen while annotate-mode is on to capture an element.",
            style = MaterialTheme.typography.bodyMedium,
        )

        // Manual override demo — annotations inside this card report
        // "LoginForm.kt:1" as Source, instead of inheriting the activity-level
        // fallback.
        Card(modifier = Modifier.jellySource("LoginForm.kt", 1).fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Login form", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { testTag = "name-field" },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {},
                        modifier = Modifier.semantics { contentDescription = "Submit form" },
                    ) {
                        Text("Submit")
                    }
                    Button(onClick = {}) { Text("Cancel") }
                }
            }
        }

        SettingsPanel()
    }
}

@Composable
private fun SettingsPanel(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Notifications: On", style = MaterialTheme.typography.bodyMedium)
            Text("Sync over Wi-Fi only", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
