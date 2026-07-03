package com.safepathbuea.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.safepathbuea.app.SafePathViewModel
import com.safepathbuea.app.data.HazardType

/** Selecting a type attaches the device's current GPS location, writes the
 * report to Firestore with severity=medium, and always returns Home once
 * the write (or its failure) has been announced. */
@Composable
fun ReportHazardScreen(
    viewModel: SafePathViewModel,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        val spokenList = HazardType.entries.joinToString(", ") { it.label }
        viewModel.tts.speak("Report hazard. Choose a type: $spokenList.")
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "Report hazard", style = MaterialTheme.typography.headlineMedium)
            for (type in HazardType.entries) {
                Button(
                    onClick = { viewModel.reportHazard(type, onComplete = onDone) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { contentDescription = "Report ${type.label}" },
                ) {
                    Text(type.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
