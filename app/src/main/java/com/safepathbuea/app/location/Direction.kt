package com.safepathbuea.app.location

/** Converts an absolute compass bearing to a hazard into an 8-point
 * direction spoken relative to which way the user is currently facing. */
fun relativeDirectionPhrase(bearingToTargetDegrees: Float, deviceHeadingDegrees: Float): String {
    val relative = ((bearingToTargetDegrees - deviceHeadingDegrees) + 360f) % 360f
    return when {
        relative < 22.5f || relative >= 337.5f -> "ahead"
        relative < 67.5f -> "ahead and to your right"
        relative < 112.5f -> "to your right"
        relative < 157.5f -> "behind and to your right"
        relative < 202.5f -> "behind you"
        relative < 247.5f -> "behind and to your left"
        relative < 292.5f -> "to your left"
        else -> "ahead and to your left"
    }
}
