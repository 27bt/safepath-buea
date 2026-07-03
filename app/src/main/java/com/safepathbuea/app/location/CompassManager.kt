package com.safepathbuea.app.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Reads device heading (0 = north, clockwise) from the rotation-vector
 * sensor so nearby-hazard bearings can be spoken relative to which way the
 * user is actually facing, not just compass-absolute. */
class CompassManager(context: Context) {

    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    @Volatile
    private var headingDegrees: Float = 0f

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuthRadians = orientation[0]
            headingDegrees = (Math.toDegrees(azimuthRadians.toDouble()).toFloat() + 360f) % 360f
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }

    fun currentHeadingDegrees(): Float = headingDegrees

    fun isAvailable(): Boolean = rotationSensor != null
}
