package com.safepathbuea.app.alert

import com.safepathbuea.app.vision.Detection
import com.safepathbuea.app.vision.HorizontalPosition
import com.safepathbuea.app.vision.Proximity

/** Turns a raw ML Kit [Detection] into a spoken [Alert]. Close + centered
 * objects are the ones actually blocking the user's path, so only those
 * get URGENT priority; anything else is informational and can wait. */
fun Detection.toAlert(): Alert {
    val position = when (horizontalPosition) {
        HorizontalPosition.LEFT -> "on your left"
        HorizontalPosition.CENTER -> "ahead"
        HorizontalPosition.RIGHT -> "on your right"
    }
    val message = when (proximity) {
        Proximity.CLOSE -> "Obstacle close $position"
        Proximity.MEDIUM -> "Object $position"
        Proximity.FAR -> "Object detected $position in the distance"
    }
    return Alert(
        cooldownKey = "camera:$horizontalPosition:$proximity",
        message = message,
        priority = if (isCloseAndInPath) AlertPriority.URGENT else AlertPriority.NORMAL,
        source = AlertSource.CAMERA,
    )
}
