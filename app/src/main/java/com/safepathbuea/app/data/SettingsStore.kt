package com.safepathbuea.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "safepath_preferences")

data class AppSettings(
    val isFrench: Boolean = false,
    val speechRate: Float = 1.0f,
    val alertRadiusMeters: Float = 50f,
    val emergencyContact: String = "",
    val autoScan: Boolean = true,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val IS_FRENCH = booleanPreferencesKey("is_french")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val ALERT_RADIUS_METERS = floatPreferencesKey("alert_radius_meters")
        val EMERGENCY_CONTACT = stringPreferencesKey("emergency_contact")
        val AUTO_SCAN = booleanPreferencesKey("auto_scan")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            isFrench = prefs[Keys.IS_FRENCH] ?: false,
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            alertRadiusMeters = prefs[Keys.ALERT_RADIUS_METERS] ?: 50f,
            emergencyContact = prefs[Keys.EMERGENCY_CONTACT] ?: "",
            autoScan = prefs[Keys.AUTO_SCAN] ?: true,
        )
    }

    suspend fun updateIsFrench(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.IS_FRENCH] = enabled }
    }

    suspend fun updateSpeechRate(rate: Float) {
        context.dataStore.edit { prefs -> prefs[Keys.SPEECH_RATE] = rate }
    }

    suspend fun updateAlertRadius(radius: Float) {
        context.dataStore.edit { prefs -> prefs[Keys.ALERT_RADIUS_METERS] = radius }
    }

    suspend fun updateEmergencyContact(contact: String) {
        context.dataStore.edit { prefs -> prefs[Keys.EMERGENCY_CONTACT] = contact }
    }

    suspend fun updateAutoScan(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.AUTO_SCAN] = enabled }
    }
}
