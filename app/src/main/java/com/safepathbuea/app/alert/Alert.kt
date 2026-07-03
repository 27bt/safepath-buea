package com.safepathbuea.app.alert

enum class AlertPriority { NORMAL, URGENT }

enum class AlertSource { CAMERA, NEARBY_HAZARD }

/**
 * @param cooldownKey identifies "the same alert type" for the 10s cooldown
 * (e.g. "camera:person" or, from Phase 3, "hazard:pothole:<geohash>").
 */
data class Alert(
    val cooldownKey: String,
    val message: String,
    val priority: AlertPriority,
    val source: AlertSource,
)
