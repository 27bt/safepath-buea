package com.safepathbuea.app.vision

enum class Proximity { CLOSE, MEDIUM, FAR }

enum class HorizontalPosition { LEFT, CENTER, RIGHT }

/**
 * A single ML Kit detection normalized against the (rotation-corrected)
 * frame dimensions, so downstream code never needs to know about camera
 * sensor orientation.
 */
data class Detection(
    val label: String,
    val areaRatio: Float,
    val centerXRatio: Float,
) {
    // "Large + horizontally centered = close + in path" per the product spec.
    val proximity: Proximity
        get() = when {
            areaRatio >= 0.25f -> Proximity.CLOSE
            areaRatio >= 0.08f -> Proximity.MEDIUM
            else -> Proximity.FAR
        }

    val horizontalPosition: HorizontalPosition
        get() = when {
            centerXRatio < 0.35f -> HorizontalPosition.LEFT
            centerXRatio > 0.65f -> HorizontalPosition.RIGHT
            else -> HorizontalPosition.CENTER
        }

    val isCloseAndInPath: Boolean
        get() = proximity == Proximity.CLOSE && horizontalPosition == HorizontalPosition.CENTER
}
