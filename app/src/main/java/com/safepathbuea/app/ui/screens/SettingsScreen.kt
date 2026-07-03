package com.safepathbuea.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.safepathbuea.app.ScanningState
import com.safepathbuea.app.SafePathViewModel
import java.util.Locale

/** Phase 4 persists these via DataStore; the offline-mode indicator is
 * still a stub until the connectivity monitor lands in Phase 4. */
@Composable
fun SettingsScreen(
    viewModel: SafePathViewModel,
    onDone: () -> Unit,
) {
    val isFrench by viewModel.isFrench.collectAsState()
    val speechRate by viewModel.speechRate.collectAsState()
    val alertRadiusMeters by viewModel.alertRadiusMeters.collectAsState()
    val scanningState by viewModel.scanningState.collectAsState()
    val emergencyContact by viewModel.emergencyContact.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.tts.speak("Settings.")
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Language: ${if (isFrench) "French" else "English"}. Double tap to toggle." },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Language: ${if (isFrench) "Français" else "English"}", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isFrench,
                    onCheckedChange = { checked -> viewModel.setFrench(checked) },
                )
            }

            Column(modifier = Modifier.semantics {
                contentDescription = "Speech rate: ${String.format(Locale.US, "%.1f", speechRate)} times normal speed"
            }) {
                Text("Speech rate: ${String.format(Locale.US, "%.1f", speechRate)}x", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = speechRate,
                    onValueChange = { viewModel.setSpeechRate(it) },
                    valueRange = 0.5f..2.0f,
                )
            }

            Column(modifier = Modifier.semantics {
                contentDescription = "Alert radius: ${alertRadiusMeters.toInt()} meters"
            }) {
                Text("Alert radius: ${alertRadiusMeters.toInt()} m", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = alertRadiusMeters,
                    onValueChange = { viewModel.setAlertRadiusMeters(it) },
                    valueRange = 20f..200f,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Auto scan: ${if (scanningState == ScanningState.SCANNING) "on" else "off"}" },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Auto-scan", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = scanningState == ScanningState.SCANNING,
                    onCheckedChange = { viewModel.setAutoScan(it) },
                )
            }

            Text(
                text = "Offline mode: not connected yet (added in Phase 4)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { contentDescription = "Offline mode indicator: not connected yet" },
            )

            OutlinedTextField(
                value = emergencyContact,
                onValueChange = { viewModel.setEmergencyContact(it) },
                label = { Text("Emergency contact phone number") },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Emergency contact phone number" },
            )

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Back to home" },
            ) {
                Text("Back to home", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
