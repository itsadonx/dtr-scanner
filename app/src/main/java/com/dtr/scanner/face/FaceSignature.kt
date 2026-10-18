package com.dtr.scanner.face

import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

object FaceSignature {
    private val contourTypes = listOf(
        FaceContour.FACE,
        FaceContour.LEFT_EYEBROW_TOP,
        FaceContour.LEFT_EYEBROW_BOTTOM,
        FaceContour.RIGHT_EYEBROW_TOP,
        FaceContour.RIGHT_EYEBROW_BOTTOM,
        FaceContour.LEFT_EYE,
        FaceContour.RIGHT_EYE,
        FaceContour.NOSE_BRIDGE,
        FaceContour.NOSE_BOTTOM,
        FaceContour.UPPER_LIP_TOP,
        FaceContour.UPPER_LIP_BOTTOM,
        FaceContour.LOWER_LIP_TOP,
        FaceContour.LOWER_LIP_BOTTOM,
        FaceContour.LEFT_CHEEK,
        FaceContour.RIGHT_CHEEK,
    )

    /** Lower is a closer match. Same person is usually under [MATCH_THRESHOLD]. */
    const val MATCH_THRESHOLD = 0.155f

    /** Best match must beat the runner-up by at least this much. */
    const val MIN_MATCH_MARGIN = 0.045f

    const val ENROLLMENT_SAMPLES = 5

    enum class ExtractMode {
        ENROLL,
        MATCH,
    }

    fun extract(face: Face, mode: ExtractMode = ExtractMode.MATCH): FloatArray? {
        val maxYaw = if (mode == ExtractMode.MATCH) 10f else 16f
        val maxPitch = if (mode == ExtractMode.MATCH) 12f else 16f
        val minFaceWidth = if (mode == ExtractMode.MATCH) 140 else 120

        if (abs(face.headEulerAngleY) > maxYaw) return null
        if (abs(face.headEulerAngleX) > maxPitch) return null
        if (face.boundingBox.width() < minFaceWidth) return null

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val centerX = (leftEye.x + rightEye.x) / 2f
        val centerY = (leftEye.y + rightEye.y) / 2f
        val scale = hypot(leftEye.x - rightEye.x, leftEye.y - rightEye.y).coerceAtLeast(1f)

        val values = mutableListOf<Float>()
        for (type in contourTypes) {
            val points = face.getContour(type)?.points ?: return null
            if (points.isEmpty()) return null
            for (point in points) {
                values.add((point.x - centerX) / scale)
                values.add((point.y - centerY) / scale)
            }
        }
        return values.toFloatArray()
    }

    fun encode(values: FloatArray): String = SIGNATURE_VERSION_PREFIX + values.joinToString(",")

    fun decode(raw: String): FloatArray? {
        val body = if (raw.startsWith(SIGNATURE_VERSION_PREFIX)) {
            raw.removePrefix(SIGNATURE_VERSION_PREFIX)
        } else {
            raw
        }
        if (body.isBlank()) return null
        return body.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray().takeIf { it.isNotEmpty() }
    }

    fun isCurrentFormat(raw: String): Boolean = raw.startsWith(SIGNATURE_VERSION_PREFIX)

    fun distance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return Float.MAX_VALUE
        var sum = 0f
        for (i in a.indices) {
            val delta = a[i] - b[i]
            sum += delta * delta
        }
        return sqrt(sum / a.size)
    }

    fun average(samples: List<FloatArray>): FloatArray? {
        if (samples.isEmpty()) return null
        val size = samples.first().size
        if (samples.any { it.size != size }) return null
        return FloatArray(size) { index ->
            samples.map { it[index] }.average().toFloat()
        }
    }

    data class MatchResult(
        val displayName: String?,
        val ambiguous: Boolean,
    )

    fun match(
        probe: FloatArray,
        profiles: List<Pair<String, FloatArray>>,
    ): MatchResult {
        if (profiles.isEmpty()) {
            return MatchResult(displayName = null, ambiguous = false)
        }

        val ranked = profiles
            .map { (name, signature) -> name to distance(probe, signature) }
            .sortedBy { it.second }

        val (bestName, bestDistance) = ranked.first()
        val secondDistance = ranked.getOrNull(1)?.second ?: Float.MAX_VALUE

        if (bestDistance > MATCH_THRESHOLD) {
            return MatchResult(displayName = null, ambiguous = false)
        }
        if ((secondDistance - bestDistance) < MIN_MATCH_MARGIN) {
            return MatchResult(displayName = null, ambiguous = true)
        }
        return MatchResult(displayName = bestName, ambiguous = false)
    }

    private const val SIGNATURE_VERSION_PREFIX = "2|"
}
