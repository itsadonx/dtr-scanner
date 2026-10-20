package com.dtr.scanner.camera

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.dtr.scanner.face.EnrollmentGuideState
import com.dtr.scanner.face.FaceCapture
import com.dtr.scanner.face.FaceLivenessTracker
import com.dtr.scanner.face.FaceSignature
import com.dtr.scanner.face.FaceSignature.ExtractMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(
    private val livenessTracker: FaceLivenessTracker,
    private val onLivenessHint: (String) -> Unit,
    private val onEnrollmentGuide: ((EnrollmentGuideState?) -> Unit)? = null,
    private val onLiveFaceCapture: (FaceCapture) -> Unit,
    private val consumeLivenessAfterCapture: Boolean = false,
    private val extractMode: ExtractMode = ExtractMode.MATCH,
    private val livenessMode: FaceLivenessTracker.Mode = FaceLivenessTracker.Mode.CHECK_IN,
    private val captureFaceThumbnail: Boolean = false,
    private val mirrorForFrontCamera: Boolean = true,
    private val matchTemplateFloatCount: Int? = null,
    private val matchTemplateSizes: Set<Int>? = null,
) : ImageAnalysis.Analyzer {

    init {
        livenessTracker.mode = livenessMode
    }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build(),
    )

    @Volatile
    private var lastEmitTimeMs: Long = 0L

    @Volatile
    private var lastHint: String? = null

    @Volatile
    private var lastEnrollmentGuide: EnrollmentGuideState? = null

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
                val face = faces.maxByOrNull { it.boundingBox.width() } ?: run {
                    publishHint("Position your face in the camera.")
                    if (livenessMode == FaceLivenessTracker.Mode.ENROLLMENT) {
                        publishEnrollmentGuide(EnrollmentGuideState.initial())
                    }
                    return@addOnSuccessListener
                }

                val liveness = livenessTracker.process(face)
                publishHint(liveness.hint)
                publishEnrollmentGuide(liveness.enrollmentGuide)

                if (!liveness.verified) {
                    return@addOnSuccessListener
                }

                val matchProbes = when (extractMode) {
                    ExtractMode.MATCH -> {
                        val sizes = matchTemplateSizes?.filter { it > 0 }?.toSet()
                        if (!sizes.isNullOrEmpty() && sizes.size > 1) {
                            sizes.mapNotNull { size ->
                                FaceSignature.extract(
                                    face,
                                    extractMode,
                                    matchTemplateFloatCount = size,
                                )
                            }
                        } else {
                            listOfNotNull(
                                FaceSignature.extract(
                                    face,
                                    extractMode,
                                    matchTemplateFloatCount = matchTemplateFloatCount,
                                ),
                            )
                        }
                    }
                    else -> listOfNotNull(
                        FaceSignature.extract(
                            face,
                            extractMode,
                            matchTemplateFloatCount = matchTemplateFloatCount,
                        ),
                    )
                }
                val signature = matchProbes.firstOrNull() ?: run {
                    publishHint(
                        if (extractMode == ExtractMode.MATCH) {
                            "Look straight at the camera, eyes open, good lighting, move a little closer."
                        } else {
                            "Face the camera directly, hold still, and move a little closer."
                        },
                    )
                    return@addOnSuccessListener
                }
                if (extractMode == ExtractMode.ENROLL && !FaceSignature.isFrontalForEnrollment(face)) {
                    publishHint("Look straight at the camera to record this sample.")
                    return@addOnSuccessListener
                }
                if (matchProbes.any { it.all { v -> v == 0f } }) {
                    publishHint("Hold still — we could not read facial landmarks.")
                    return@addOnSuccessListener
                }
                val now = System.currentTimeMillis()
                if (now - lastEmitTimeMs < EMIT_COOLDOWN_MS) return@addOnSuccessListener
                lastEmitTimeMs = now
                val thumbnail = if (captureFaceThumbnail) {
                    try {
                        FaceThumbnailCropper.cropToJpeg(
                            imageProxy = imageProxy,
                            face = face,
                            mirrorForFrontCamera = mirrorForFrontCamera,
                        )
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
                onLiveFaceCapture(
                    FaceCapture(
                        signature = signature,
                        thumbnailJpeg = thumbnail,
                        matchProbes = matchProbes,
                    ),
                )
                if (consumeLivenessAfterCapture) {
                    livenessTracker.consumeVerification()
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun publishHint(hint: String) {
        if (hint == lastHint) return
        lastHint = hint
        onLivenessHint(hint)
    }

    private fun publishEnrollmentGuide(guide: EnrollmentGuideState?) {
        if (guide == lastEnrollmentGuide) return
        lastEnrollmentGuide = guide
        onEnrollmentGuide?.invoke(guide)
    }

    companion object {
        private const val EMIT_COOLDOWN_MS = 550L
    }
}
