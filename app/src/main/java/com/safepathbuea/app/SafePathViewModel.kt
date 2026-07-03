package com.safepathbuea.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.camera.core.Preview
import com.firebase.geofire.GeoLocation
import com.safepathbuea.app.alert.Alert
import com.safepathbuea.app.alert.AlertManager
import com.safepathbuea.app.alert.AlertPriority
import com.safepathbuea.app.alert.AlertSource
import com.safepathbuea.app.alert.toAlert
import com.safepathbuea.app.data.AuthManager
import com.safepathbuea.app.data.ConnectivityMonitor
import com.safepathbuea.app.data.HazardRepository
import com.safepathbuea.app.data.HazardType
import com.safepathbuea.app.data.HazardWithDistance
import com.safepathbuea.app.data.NearbyHazardUi
import com.safepathbuea.app.data.OfflineReport
import com.safepathbuea.app.data.OfflineReportDatabase
import com.safepathbuea.app.data.SettingsStore
import com.safepathbuea.app.location.CompassManager
import com.safepathbuea.app.location.NominatimGeocoder
import com.safepathbuea.app.location.UserLocationProvider
import com.safepathbuea.app.location.relativeDirectionPhrase
import com.safepathbuea.app.speech.SpeechPriority
import com.safepathbuea.app.speech.TtsManager
import com.safepathbuea.app.vision.CameraController
import com.safepathbuea.app.vision.Detection
import com.safepathbuea.app.vision.ObjectDetectionAnalyzer
import com.safepathbuea.app.voice.ListeningState
import com.safepathbuea.app.voice.VoiceCommand
import com.safepathbuea.app.voice.VoiceCommandManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

enum class ScanningState { SCANNING, PAUSED }

private const val NEARBY_POLL_INTERVAL_MILLIS = 30_000L
private const val NEARBY_POLL_ALERT_RADIUS_METERS = 15.0

/**
 * Activity-scoped hub for TTS, voice commands, the camera/alert pipeline,
 * Firebase, and in-memory settings, shared by all four screens. Settings
 * become persistent (DataStore) and the offline queue arrives in Phase 4.
 */
class SafePathViewModel(application: Application) : AndroidViewModel(application) {

    val tts = TtsManager(application)
    val voice = VoiceCommandManager(application)

    val listeningState: StateFlow<ListeningState> = voice.state

    private val _scanningState = MutableStateFlow(ScanningState.SCANNING)
    val scanningState: StateFlow<ScanningState> = _scanningState.asStateFlow()

    private val _commandLog = MutableStateFlow<List<String>>(emptyList())
    val commandLog: StateFlow<List<String>> = _commandLog.asStateFlow()

    private val _locationStatus = MutableStateFlow("Location not yet available")
    val locationStatus: StateFlow<String> = _locationStatus.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastAlert = MutableStateFlow<String?>(null)
    val lastAlert: StateFlow<String?> = _lastAlert.asStateFlow()

    // --- Settings persisted via DataStore in Phase 4 ---
    private val _isFrench = MutableStateFlow(false)
    val isFrench: StateFlow<Boolean> = _isFrench.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _alertRadiusMeters = MutableStateFlow(50f)
    val alertRadiusMeters: StateFlow<Float> = _alertRadiusMeters.asStateFlow()

    private val _emergencyContact = MutableStateFlow("")
    val emergencyContact: StateFlow<String> = _emergencyContact.asStateFlow()

    private val cameraController = CameraController(application)
    private val detectionAnalyzer = ObjectDetectionAnalyzer(
        onDetections = ::handleDetections,
        onError = { error -> appendLog("Detection error: ${error.message}") },
    )
    private val alertManager = AlertManager(
        tts = tts,
        scope = viewModelScope,
        onAlertAnnounced = { alert -> _lastAlert.value = alert.message },
    )

    private val settingsStore = SettingsStore(application)
    private val authManager = AuthManager()
    private val hazardRepository = HazardRepository()
    private val offlineReportDao = OfflineReportDatabase.getInstance(application).offlineReportDao()
    private val locationProvider = UserLocationProvider(application)
    private val compassManager = CompassManager(application)
    private val nominatimGeocoder = NominatimGeocoder()
    private val connectivityMonitor = ConnectivityMonitor(application)

    init {
        compassManager.start()
        connectivityMonitor.start()
        viewModelScope.launch {
            settingsStore.settings.collect { saved ->
                _isFrench.value = saved.isFrench
                _speechRate.value = saved.speechRate
                _alertRadiusMeters.value = saved.alertRadiusMeters
                _emergencyContact.value = saved.emergencyContact
                setScanning(saved.autoScan)
                tts.setLanguage(if (saved.isFrench) Locale.FRENCH else Locale.ENGLISH)
                tts.setSpeechRate(saved.speechRate)
            }
        }
        viewModelScope.launch {
            connectivityMonitor.isConnected.collect { connected ->
                if (connected) {
                    tts.speak("Connectivity restored. Syncing queued hazard reports.")
                    syncOfflineReports()
                } else {
                    tts.speak("You are offline. Reports will be queued locally.")
                }
            }
        }
        viewModelScope.launch {
            runCatching { authManager.ensureSignedIn() }
                .onFailure { appendLog("Sign-in failed: ${it.message}") }
        }
        startNearbyHazardPolling()
    }

    /** Called once from MainActivity after the CAMERA permission is granted. */
    fun startCamera(lifecycleOwner: LifecycleOwner) {
        cameraController.start(lifecycleOwner, detectionAnalyzer) { error ->
            appendLog("Camera error: ${error.message}")
        }
    }

    /** Sighted-tester aid only, off by default - see [CameraController]. */
    fun setDebugCameraPreview(surfaceProvider: Preview.SurfaceProvider?) {
        cameraController.setDebugPreviewSurfaceProvider(surfaceProvider)
    }

    private fun handleDetections(detections: List<Detection>) {
        if (_scanningState.value != ScanningState.SCANNING) return
        // Only the single most significant detection per frame is worth
        // speaking; the rest would just be queue noise a second later.
        val mostSignificant = detections.maxByOrNull { it.areaRatio } ?: return
        alertManager.submit(mostSignificant.toAlert())
    }

    fun startListening(onCommand: (VoiceCommand) -> Unit = {}) {
        voice.startListening(
            onResult = { heard -> onCommand(onSpeechRecognized(heard)) },
            onError = { message -> appendLog("Voice error: $message") },
        )
    }

    fun stopListening() {
        voice.stopListening()
    }

    /** Parses and reacts to a recognized phrase; returns the command so the
     * caller (which owns the NavController) can act on navigation cases. */
    fun onSpeechRecognized(heardText: String): VoiceCommand {
        val command = VoiceCommand.parse(heardText)
        appendLog("Heard: \"$heardText\" -> $command")

        when (command) {
            is VoiceCommand.WhatsAhead -> {
                val text = lastAlert.value?.let { "Last detection: $it" }
                    ?: "Nothing detected ahead right now."
                announceAlert(text)
            }
            is VoiceCommand.WhereAmI -> speakCurrentLocation()
            is VoiceCommand.Repeat -> tts.repeatLast()
            is VoiceCommand.Help -> tts.speak(
                "Available commands: what's ahead, where am I, report hazard, nearby hazards, repeat, help, stop, resume, call for help."
            )
            is VoiceCommand.Stop -> {
                setScanning(false)
                tts.speak("Scanning paused.")
            }
            is VoiceCommand.Resume -> {
                setScanning(true)
                tts.speak("Scanning resumed.")
            }
            is VoiceCommand.CallForHelp -> {
                if (_emergencyContact.value.isBlank()) {
                    tts.speak("No emergency contact saved yet. Add one in Settings.")
                } else {
                    tts.speak("Calling emergency contact.")
                }
            }
            is VoiceCommand.ReportHazard, is VoiceCommand.NearbyHazards -> {
                // Navigation is handled by the caller; just acknowledge here.
            }
            is VoiceCommand.Unrecognized -> tts.speak("Sorry, I didn't understand that command.")
        }
        return command
    }

    fun setScanning(enabled: Boolean) {
        _scanningState.value = if (enabled) ScanningState.SCANNING else ScanningState.PAUSED
        detectionAnalyzer.isEnabled = enabled
    }

    fun setFrench(enabled: Boolean) {
        _isFrench.value = enabled
        viewModelScope.launch { settingsStore.updateIsFrench(enabled) }
        tts.setLanguage(if (enabled) Locale.FRENCH else Locale.ENGLISH)
        tts.speak(if (enabled) "Langue changée en français." else "Language changed to English.")
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        viewModelScope.launch { settingsStore.updateSpeechRate(rate) }
        tts.setSpeechRate(rate)
    }

    fun setAlertRadiusMeters(radius: Float) {
        _alertRadiusMeters.value = radius.coerceIn(20f, 200f)
        viewModelScope.launch { settingsStore.updateAlertRadius(_alertRadiusMeters.value) }
    }

    fun setEmergencyContact(phoneNumber: String) {
        _emergencyContact.value = phoneNumber
        viewModelScope.launch { settingsStore.updateEmergencyContact(phoneNumber) }
    }

    fun setAutoScan(enabled: Boolean) {
        viewModelScope.launch { settingsStore.updateAutoScan(enabled) }
        setScanning(enabled)
    }

    fun emergencyPhoneNumber(): String? = _emergencyContact.value.takeIf { it.isNotBlank() }

    fun announceAlert(text: String, priority: SpeechPriority = SpeechPriority.NORMAL) {
        _lastAlert.value = text
        tts.speak(text, priority)
    }

    fun repeatLastAlert() {
        tts.repeatLast()
    }

    fun refreshLocationStatus() {
        viewModelScope.launch {
            val location = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            _locationStatus.value = if (location == null) {
                "Location not available"
            } else {
                val addressText = resolveAddress(location.latitude, location.longitude)
                if (addressText != null) {
                    addressText
                } else {
                    val lat = String.format(Locale.US, "%.5f", location.latitude)
                    val lng = String.format(Locale.US, "%.5f", location.longitude)
                    "Lat $lat, Lon $lng"
                }
            }
        }
    }

    private fun speakCurrentLocation() {
        viewModelScope.launch {
            val location = runCatching { locationProvider.getCurrentLocation() }.getOrNull()
            if (location == null) {
                tts.speak("Location is not available right now.")
                return@launch
            }

            val addressText = resolveAddress(location.latitude, location.longitude)
            if (addressText != null) {
                tts.speak("You are at $addressText.")
            } else {
                val lat = String.format(Locale.US, "%.5f", location.latitude)
                val lng = String.format(Locale.US, "%.5f", location.longitude)
                tts.speak("Your current location is latitude $lat, longitude $lng.")
            }
        }
    }

    /** Tries OpenStreetMap's Nominatim first - its community-tagged
     * neighborhood data for Buea is far denser than Google's, which mostly
     * falls back to Plus Codes here. Falls through to Android's Geocoder,
     * then the small hardcoded local table, blending in the local name
     * whenever it isn't already implied by the resolved address. */
    private suspend fun resolveAddress(latitude: Double, longitude: Double): String? {
        val localName = resolveLocalNeighborhood(latitude, longitude)

        val osmAddress = nominatimGeocoder.reverseGeocode(latitude, longitude)
        if (!osmAddress.isNullOrBlank()) {
            return blendWithLocalName(osmAddress, localName)
        }

        val geocoderAddress = runCatching {
            val geocoder = Geocoder(getApplication<Application>())
            val addresses = geocodeFromLocation(geocoder, latitude, longitude, maxResults = 5)
            val bestAddress = addresses?.firstOrNull { address ->
                address.locality != null || address.subLocality != null || address.thoroughfare != null || address.featureName != null
            } ?: addresses?.firstOrNull()
            bestAddress?.let(::formatAddress)
        }.getOrNull()

        return when {
            !geocoderAddress.isNullOrBlank() -> blendWithLocalName(geocoderAddress, localName)
            localName != null -> "$localName, Buea"
            else -> null
        }
    }

    private fun blendWithLocalName(address: String, localName: String?): String =
        if (localName != null && !address.contains(localName, ignoreCase = true)) {
            "$localName, $address"
        } else {
            address
        }

    /** API 33+ has a non-blocking listener overload; below that, the only
     * option is the deprecated synchronous call, run off the main thread. */
    private suspend fun geocodeFromLocation(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double,
        maxResults: Int,
    ): List<Address>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, maxResults) { addresses ->
                    continuation.resume(addresses)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, maxResults)
            }
        }
    }

    private fun formatAddress(address: Address): String? {
        val parts = listOfNotNull(
            address.featureName,
            address.thoroughfare,
            address.subThoroughfare,
            address.subLocality,
            address.locality,
            address.adminArea,
            address.countryName,
        ).distinct()
        val joined = parts.joinToString(", ")
        return if (joined.isNotBlank()) joined else address.getAddressLine(0)
    }

    private fun resolveLocalNeighborhood(latitude: Double, longitude: Double): String? {
        return localNeighborhoods.firstOrNull { it.contains(latitude, longitude) }?.name
    }

    private data class LocalNeighborhood(
        val name: String,
        val minLat: Double,
        val maxLat: Double,
        val minLng: Double,
        val maxLng: Double,
    ) {
        fun contains(lat: Double, lng: Double): Boolean =
            lat in minLat..maxLat && lng in minLng..maxLng
    }

    private val localNeighborhoods = listOf(
        LocalNeighborhood(
            name = "UB Junction",
            minLat = 4.1475,
            maxLat = 4.1585,
            minLng = 9.2360,
            maxLng = 9.2470,
        ),
        LocalNeighborhood(
            name = "Tarred Malingo",
            minLat = 4.1300,
            maxLat = 4.1500,
            minLng = 9.2280,
            maxLng = 9.2480,
        ),
        LocalNeighborhood(
            name = "Buea Town",
            minLat = 4.1400,
            maxLat = 4.1700,
            minLng = 9.2200,
            maxLng = 9.2600,
        ),
    )
    
    private suspend fun syncOfflineReports() {
        if (!connectivityMonitor.isConnected.value) return
        val offlineReports = offlineReportDao.allReports()
        offlineReports.forEach { report ->
            runCatching {
                val location = android.location.Location("offline").apply {
                    latitude = report.latitude
                    longitude = report.longitude
                }
                hazardRepository.reportHazard(report.type, location, report.hashedUid).getOrThrow()
                offlineReportDao.delete(report)
            }.onFailure {
                appendLog("Offline sync failed for report ${report.id}: ${it.message}")
            }
        }
    }

    /** Writes a hazard report for [type] at the device's current location,
     * then always calls [onComplete] (used by ReportHazardScreen to return
     * Home regardless of success or failure). */
    fun reportHazard(type: HazardType, onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching { performReportHazard(type) }
                .onFailure {
                    appendLog("Report failed: ${it.message}")
                    tts.speak("Sorry, the report could not be saved.")
                }
            onComplete()
        }
    }

    /** Tries a live Firestore write when online; on failure, or when
     * offline to begin with, falls back to the local queue so a report is
     * never silently lost - only [syncOfflineReports] and connectivity
     * loss/unavailable-location cases speak without writing anything. */
    private suspend fun performReportHazard(type: HazardType) {
        val uid = authManager.ensureSignedIn()
        val hashedUid = authManager.hashedUid(uid)
        val location = locationProvider.getCurrentLocation()
        if (location == null) {
            tts.speak("Location unavailable. Please make sure GPS is enabled.")
            return
        }

        suspend fun queueOffline() {
            offlineReportDao.insert(
                OfflineReport(
                    type = type.id,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    hashedUid = hashedUid,
                )
            )
        }

        if (connectivityMonitor.isConnected.value) {
            val result = hazardRepository.reportHazard(type.id, location, hashedUid)
            if (result.isSuccess) {
                tts.speak("${type.label} reported. Thank you.")
            } else {
                appendLog("Report failed, queuing offline: ${result.exceptionOrNull()?.message}")
                queueOffline()
                tts.speak("The report could not be sent right now. It has been queued and will be synced when connectivity returns.")
            }
        } else {
            queueOffline()
            tts.speak("You are offline. The hazard report is queued and will be synced when connectivity returns.")
        }
    }

    /** Queries hazards within the configured alert radius and returns a
     * UI-ready, closest-first list; also speaks a summary. */
    fun loadNearbyHazards(onResult: (List<NearbyHazardUi>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val location = locationProvider.getCurrentLocation() ?: error("Location unavailable")
                val hazards = hazardRepository.queryNearbyHazards(
                    GeoLocation(location.latitude, location.longitude),
                    _alertRadiusMeters.value.toDouble(),
                )
                hazards.map { it.toUiModel(location.bearingTo(it.toAndroidLocation())) }
            }
            val hazards = result.getOrDefault(emptyList())
            onResult(hazards)

            if (result.isFailure) {
                tts.speak("Could not check nearby hazards. Please check your connection.")
                return@launch
            }
            if (hazards.isEmpty()) {
                tts.speak("No hazards reported nearby.")
            } else {
                val spoken = hazards.joinToString(". ") { hazard ->
                    val direction = relativeDirectionPhrase(hazard.bearingDegrees, compassManager.currentHeadingDegrees())
                    val reportWord = if (hazard.confidenceCount == 1L) "report" else "reports"
                    "${hazard.typeLabel}, ${hazard.distanceMeters} meters $direction, confirmed by ${hazard.confidenceCount} $reportWord"
                }
                tts.speak("${hazards.size} hazards found nearby. $spoken.")
            }
        }
    }

    private fun HazardWithDistance.toAndroidLocation(): android.location.Location {
        val loc = android.location.Location("hazard")
        loc.latitude = hazard.location?.latitude ?: 0.0
        loc.longitude = hazard.location?.longitude ?: 0.0
        return loc
    }

    private fun HazardWithDistance.toUiModel(bearingDegrees: Float): NearbyHazardUi = NearbyHazardUi(
        typeLabel = hazard.type.replace('_', ' '),
        distanceMeters = distanceMeters.roundToInt(),
        bearingDegrees = bearingDegrees,
        confidenceCount = hazard.confidenceCount,
    )

    private fun startNearbyHazardPolling() {
        viewModelScope.launch {
            while (true) {
                delay(NEARBY_POLL_INTERVAL_MILLIS)
                if (_scanningState.value != ScanningState.SCANNING) continue
                runCatching {
                    val location = locationProvider.getCurrentLocation() ?: return@runCatching
                    val hazards = hazardRepository.queryNearbyHazards(
                        GeoLocation(location.latitude, location.longitude),
                        _alertRadiusMeters.value.toDouble(),
                    )
                    hazards.filter { it.distanceMeters <= NEARBY_POLL_ALERT_RADIUS_METERS }.forEach { hwd ->
                        alertManager.submit(
                            Alert(
                                cooldownKey = "hazard:${hwd.hazard.id}",
                                message = "${hwd.hazard.type.replace('_', ' ')} reported nearby, " +
                                    "about ${hwd.distanceMeters.roundToInt()} meters away",
                                priority = AlertPriority.NORMAL,
                                source = AlertSource.NEARBY_HAZARD,
                            )
                        )
                    }
                }
            }
        }
    }

    private fun appendLog(entry: String) {
        _commandLog.value = (_commandLog.value + entry).takeLast(20)
    }

    override fun onCleared() {
        tts.shutdown()
        voice.stopListening()
        cameraController.shutdown()
        detectionAnalyzer.shutdown()
        compassManager.stop()
        connectivityMonitor.stop()
        super.onCleared()
    }
}
