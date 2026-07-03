package com.safepathbuea.app.vision

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

private const val TAG = "ObjectDetectionAnalyzer"

/**
 * Runs ML Kit's on-device streaming object detector at roughly [minIntervalMillis]
 * cadence (~1 fps by default) rather than on every frame CameraX delivers.
 * [isEnabled] lets voice "stop"/"resume" pause inference (and its battery
 * cost) without tearing down and rebinding the whole CameraX pipeline.
 */
class ObjectDetectionAnalyzer(
    private val onDetections: (List<Detection>) -> Unit,
    private val onError: (Exception) -> Unit = {},
    private val minIntervalMillis: Long = 1000L,
) : ImageAnalysis.Analyzer {

    @Volatile
    var isEnabled: Boolean = true

    // SINGLE_IMAGE_MODE, not STREAM_MODE: we deliberately sample at ~1 fps
    // for battery, but STREAM_MODE's cross-frame tracking is built around a
    // continuous ~30fps stream and needs frame-to-frame continuity to
    // confirm an object as real. At 1 fps that assumption breaks, which can
    // suppress detections entirely; SINGLE_IMAGE_MODE scores each frame
    // independently, which matches our sampling pattern.
    private val detector: ObjectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    private var lastProcessedAtMillis = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val mediaImage = imageProxy.image
        if (!isEnabled || mediaImage == null || now - lastProcessedAtMillis < minIntervalMillis) {
            imageProxy.close()
            return
        }
        lastProcessedAtMillis = now

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        // ML Kit returns bounding boxes in the upright (rotation-corrected)
        // frame, so width/height must be swapped for the 90/270 case too.
        val frameWidth: Int
        val frameHeight: Int
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            frameWidth = imageProxy.height
            frameHeight = imageProxy.width
        } else {
            frameWidth = imageProxy.width
            frameHeight = imageProxy.height
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        Log.d(
            TAG,
            "processing frame: ${imageProxy.width}x${imageProxy.height} format=${imageProxy.format} " +
                "rotation=$rotationDegrees planes=${imageProxy.planes.size}",
        )
        detector.process(inputImage)
            .addOnSuccessListener { detectedObjects ->
                Log.d(TAG, "frame analyzed: ${detectedObjects.size} object(s) returned")
                val detections = detectedObjects.map { obj ->
                    val box = obj.boundingBox
                    Detection(
                        label = obj.labels.firstOrNull()?.text ?: "object",
                        areaRatio = (box.width().toFloat() * box.height().toFloat()) /
                            (frameWidth.toFloat() * frameHeight.toFloat()),
                        centerXRatio = box.exactCenterX() / frameWidth.toFloat(),
                    )
                }
                if (detections.isNotEmpty()) onDetections(detections)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "detection failed", exception)
                onError(exception)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun shutdown() {
        detector.close()
    }
}
