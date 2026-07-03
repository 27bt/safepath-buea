package com.safepathbuea.app.data

import android.location.Location
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.tasks.await

data class HazardWithDistance(val hazard: HazardDocument, val distanceMeters: Double)

private const val HAZARDS_COLLECTION = "hazards"

/** Handles the two Firestore operations the app needs: writing a new hazard
 * report, and reading hazards within a radius via a geohash bounding-box
 * query (geofire-common gives us the query bounds; Firestore can't do a
 * true radius query on its own). */
class HazardRepository(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    suspend fun reportHazard(
        type: String,
        location: Location,
        hashedUid: String,
        severity: String = "medium",
    ): Result<Unit> = runCatching {
        val geoLocation = GeoLocation(location.latitude, location.longitude)
        val geohash = GeoFireUtils.getGeoHashForLocation(geoLocation)
        val document = hashMapOf(
            "type" to type,
            "location" to GeoPoint(location.latitude, location.longitude),
            "geohash" to geohash,
            "severity" to severity,
            "confidence_count" to 1L,
            "confirmed_by" to listOf(hashedUid),
            "status" to "active",
            "source" to "user_report",
            "created_at" to FieldValue.serverTimestamp(),
            "last_confirmed_at" to FieldValue.serverTimestamp(),
            "reporter_id" to hashedUid,
        )
        firestore.collection(HAZARDS_COLLECTION).add(document).await()
        Unit
    }

    /** Returns hazards within [radiusMeters] of [center], closest first. The
     * geohash bounding boxes over-select, so results are re-filtered by
     * true great-circle distance before being returned. */
    suspend fun queryNearbyHazards(center: GeoLocation, radiusMeters: Double): List<HazardWithDistance> {
        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusMeters)
        val matched = LinkedHashMap<String, HazardWithDistance>()

        for (bound in bounds) {
            val snapshot = firestore.collection(HAZARDS_COLLECTION)
                .whereEqualTo("status", "active")
                .orderBy("geohash")
                .startAt(bound.startHash)
                .endAt(bound.endHash)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val hazard = doc.toObject(HazardDocument::class.java) ?: continue
                val point = hazard.location ?: continue
                val distance = GeoFireUtils.getDistanceBetween(GeoLocation(point.latitude, point.longitude), center)
                if (distance <= radiusMeters) {
                    matched[hazard.id] = HazardWithDistance(hazard, distance)
                }
            }
        }

        return matched.values.sortedBy { it.distanceMeters }
    }
}
