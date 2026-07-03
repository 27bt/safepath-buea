package com.safepathbuea.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.camera.view.PreviewView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.safepathbuea.app.R
import com.safepathbuea.app.ScanningState
import com.safepathbuea.app.SafePathViewModel
import com.safepathbuea.app.voice.ListeningState
import com.safepathbuea.app.voice.VoiceCommand

@Composable
fun HomeScreen(
    viewModel: SafePathViewModel,
    onReportHazard: () -> Unit,
    onNearbyHazards: () -> Unit,
    onSettings: () -> Unit,
    onCallForHelp: () -> Unit,
) {
    val scanningState by viewModel.scanningState.collectAsState()
    val listeningState by viewModel.listeningState.collectAsState()
    val lastAlert by viewModel.lastAlert.collectAsState()
    val commandLog by viewModel.commandLog.collectAsState()
    val locationStatus by viewModel.locationStatus.collectAsState()
    var debugPreviewEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshLocationStatus()
        viewModel.tts.speak("Home. $locationStatus")
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { viewModel.repeatLastAlert() },
                )
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            StatusPill(scanningState = scanningState, listeningState = listeningState)

            Spacer(modifier = Modifier.height(12.dp))

            LastAlertCard(lastAlert = lastAlert)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Location: $locationStatus",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { contentDescription = "Location status: $locationStatus" },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sighted-tester aid only: off by default, never used by blind
            // users, and not part of the spec'd Settings surface - it exists
            // purely so a developer can verify camera aim during testing.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Debug camera preview, testing aid only: ${if (debugPreviewEnabled) "on" else "off"}" },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Debug camera preview (testing only)", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = debugPreviewEnabled, onCheckedChange = { debugPreviewEnabled = it })
            }
            if (debugPreviewEnabled) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 4.dp),
                    factory = { ctx ->
                        PreviewView(ctx).also { previewView ->
                            viewModel.setDebugCameraPreview(previewView.surfaceProvider)
                        }
                    },
                    onRelease = { viewModel.setDebugCameraPreview(null) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onReportHazard,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .semantics { contentDescription = "Report hazard" },
                ) {
                    Text(stringResource(R.string.report_hazard), style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onNearbyHazards,
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp)
                        .semantics { contentDescription = "Nearby hazards" },
                ) {
                    Text(stringResource(R.string.nearby_hazards), style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { contentDescription = "Settings" },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(16.dp))

            CommandLogList(commandLog = commandLog, modifier = Modifier.weight(1f))

            HoldToSpeakButton(
                isListening = listeningState == ListeningState.LISTENING,
                onPressStart = {
                    viewModel.startListening { command ->
                        when (command) {
                            is VoiceCommand.ReportHazard -> onReportHazard()
                            is VoiceCommand.NearbyHazards -> onNearbyHazards()
                            is VoiceCommand.CallForHelp -> onCallForHelp()
                            else -> Unit
                        }
                    }
                },
                onPressEnd = { viewModel.stopListening() },
            )
        }
    }
}

@Composable
private fun StatusPill(scanningState: ScanningState, listeningState: ListeningState) {
    val label = when {
        listeningState == ListeningState.LISTENING -> "Listening"
        listeningState == ListeningState.PROCESSING -> "Processing voice command"
        scanningState == ScanningState.SCANNING -> "Scanning"
        else -> "Paused"
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { contentDescription = "Status: $label" },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun LastAlertCard(lastAlert: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Last alert: ${lastAlert ?: "none yet"}" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Last alert", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = lastAlert ?: "None yet",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun CommandLogList(commandLog: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        commandLog.takeLast(5).forEach { entry ->
            Text(
                text = entry,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun HoldToSpeakButton(
    isListening: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
) {
    val label = if (isListening) "Listening, release to send" else "Hold to speak a command"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(top = 8.dp)
            .background(
                color = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(24.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressStart()
                        tryAwaitRelease()
                        onPressEnd()
                    },
                )
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
