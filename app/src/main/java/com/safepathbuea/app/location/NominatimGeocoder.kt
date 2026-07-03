package com.safepathbuea.app.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Reverse geocodes via OpenStreetMap's public Nominatim API. OSM's
 * community mapping has far denser neighborhood-level tagging for Buea
 * than Google's geocoder, which mostly falls back to Plus Codes here.
 * Uses the free public instance under its usage policy (identifying
 * User-Agent, no bulk/cached use) - fine for this app's occasional
 * "where am I" and hazard-report lookups, but would need a self-hosted or
 * paid instance if usage ever grows past light, interactive traffic.
 */
class NominatimGeocoder {

    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                String.format(
                    Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1",
                    latitude,
                    longitude,
                )
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "SafePathBuea/1.0 (accessibility navigation app)")
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val address = json.optJSONObject("address")

            val place = address?.pickFirstNonBlank("neighbourhood", "suburb", "quarter", "hamlet", "residential", "road")
            val locality = address?.pickFirstNonBlank("city", "town", "village", "county")

            when {
                !place.isNullOrBlank() && !locality.isNullOrBlank() && !place.equals(locality, ignoreCase = true) ->
                    "$place, $locality"
                !place.isNullOrBlank() -> place
                !locality.isNullOrBlank() -> locality
                else -> json.optString("display_name").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun JSONObject.pickFirstNonBlank(vararg keys: String): String? =
        keys.asSequence()
            .map { optString(it) }
            .firstOrNull { it.isNotBlank() }
}
