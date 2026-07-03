package com.safepathbuea.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.safepathbuea.app.ui.navigation.SafePathNavHost
import com.safepathbuea.app.ui.theme.SafePathBueaTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SafePathViewModel by viewModels()
    private var cameraStarted = false

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        maybeStartCamera()
        if (!hasPermission(Manifest.permission.CAMERA)) {
            viewModel.tts.speak("Camera permission is required for hazard detection.")
        }
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            viewModel.tts.speak("Microphone permission is required for voice commands.")
        }
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            viewModel.tts.speak("Location permission is required for hazard reporting and nearby alerts.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val required = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val missing = required.filterNot { hasPermission(it) }
        if (missing.isNotEmpty()) {
            requestPermissions.launch(missing.toTypedArray())
        } else {
            viewModel.startCamera(this)
        }

        setContent {
            SafePathBueaTheme {
                SafePathNavHost(
                    viewModel = viewModel,
                    onCallForHelp = { dialEmergencyContact(viewModel.emergencyContact.value) },
                )
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun maybeStartCamera() {
        if (!cameraStarted && hasPermission(Manifest.permission.CAMERA)) {
            cameraStarted = true
            viewModel.startCamera(this)
        }
    }

    private fun dialEmergencyContact(phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        startActivity(intent)
    }
}
