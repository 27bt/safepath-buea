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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.safepathbuea.app.SafePathViewModel
import com.safepathbuea.app.data.NearbyHazardUi

private enum class LoadState { LOADING, LOADED }

/** Runs the geohash radius query on open and reads results aloud
 * closest-first; the on-screen list is a secondary aid for low-vision
 * users or sighted companions, TTS is the primary channel. */
@Composable
fun NearbyHazardsScreen(
    viewModel: SafePathViewModel,
    onDone: () -> Unit,
) {
    var loadState by remember { mutableStateOf(LoadState.LOADING) }
    var hazards by remember { mutableStateOf<List<NearbyHazardUi>>(emptyList()) }

    LaunchedEffect(Unit) {
        viewModel.tts.speak("Nearby hazards. Checking for reports near you.")
        viewModel.loadNearbyHazards { result ->
            hazards = result
            loadState = LoadState.LOADED
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Nearby hazards", style = MaterialTheme.typography.headlineMedium)

            when (loadState) {
                LoadState.LOADING -> Text(
                    text = "Checking for reports near you…",
                    style = MaterialTheme.typography.bodyLarge,
                )
                LoadState.LOADED -> {
                    if (hazards.isEmpty()) {
                        Text("No hazards reported nearby.", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            hazards.forEach { hazard ->
                                val description = "${hazard.typeLabel}, ${hazard.distanceMeters} meters away, " +
                                    "confirmed by ${hazard.confidenceCount}"
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.semantics { contentDescription = description },
                                )
                            }
                        }
                    }
                }
            }

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
