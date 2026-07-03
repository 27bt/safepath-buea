package com.safepathbuea.app.vision

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Binds an [ImageAnalysis] use case to the given lifecycle. There is no
 * Preview use case bound by default: the UI spec forbids rendering a camera
 * preview to blind users to save battery, and CameraX doesn't require one
 * just to run analysis. [setDebugPreviewSurfaceProvider] exists purely as a
 * sighted-tester aid (gated behind a Settings toggle, off by default) and
 * must never be wired into the production blind-first flow.
 */
class CameraController(private val context: Context) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var debugPreview: Preview? = null
    private var onErrorCallback: (Exception) -> Unit = {}

    fun start(lifecycleOwner: LifecycleOwner, analyzer: ObjectDetectionAnalyzer, onError: (Exception) -> Unit = {}) {
        this.lifecycleOwner = lifecycleOwner
        this.onErrorCallback = onError
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
                imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                rebind()
            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Debug-only: attaches (or detaches, via null) a live preview surface
     * alongside analysis so a sighted tester can see what the back camera
     * sees. Never used in the production blind-first UX. */
    fun setDebugPreviewSurfaceProvider(surfaceProvider: Preview.SurfaceProvider?) {
        debugPreview = surfaceProvider?.let { Preview.Builder().build().apply { setSurfaceProvider(it) } }
        if (cameraProvider != null) rebind()
    }

    private fun rebind() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        val analysis = imageAnalysis ?: return
        try {
            provider.unbindAll()
            val useCases = listOfNotNull(analysis, debugPreview).toTypedArray()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases)
        } catch (e: Exception) {
            onErrorCallback(e)
        }
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    fun shutdown() {
        stop()
        analysisExecutor.shutdown()
    }
}
