package com.safepathbuea.app.data

/** Presentation-ready hazard summary for the Nearby Hazards screen/speech. */
data class NearbyHazardUi(
    val typeLabel: String,
    val distanceMeters: Int,
    val bearingDegrees: Float,
    val confidenceCount: Long,
)
