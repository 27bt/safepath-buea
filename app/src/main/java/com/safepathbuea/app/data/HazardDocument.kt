package com.safepathbuea.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName

/** Mirrors the "hazards" Firestore collection schema exactly; field names
 * use @PropertyName so the wire format stays snake_case while Kotlin code
 * stays idiomatic camelCase. */
data class HazardDocument(
    @DocumentId
    val id: String = "",
    val type: String = "",
    val location: GeoPoint? = null,
    val geohash: String = "",
    val severity: String = "medium",
    @get:PropertyName("confidence_count") @set:PropertyName("confidence_count")
    var confidenceCount: Long = 1,
    @get:PropertyName("confirmed_by") @set:PropertyName("confirmed_by")
    var confirmedBy: List<String> = emptyList(),
    var status: String = "active",
    var source: String = "user_report",
    @get:PropertyName("created_at") @set:PropertyName("created_at")
    var createdAt: Timestamp? = null,
    @get:PropertyName("last_confirmed_at") @set:PropertyName("last_confirmed_at")
    var lastConfirmedAt: Timestamp? = null,
    @get:PropertyName("reporter_id") @set:PropertyName("reporter_id")
    var reporterId: String = "",
)
