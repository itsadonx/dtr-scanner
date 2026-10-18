package com.dtr.scanner.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.dtr.scanner.face.FaceSignature
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(
    private val onFaceSignature: (FloatArray) -> Unit,
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build(),
    )

    @Volatile
    private var lastEmitTimeMs: Long = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() } ?: return@addOnSuccessListener
                val signature = FaceSignature.extract(face) ?: return@addOnSuccessListener
                val now = System.currentTimeMillis()
                if (now - lastEmitTimeMs < EMIT_COOLDOWN_MS) return@addOnSuccessListener
                lastEmitTimeMs = now
                onFaceSignature(signature)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    companion object {
        private const val EMIT_COOLDOWN_MS = 800L
    }
}
