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
    const val MATCH_THRESHOLD = 0.20f

    /** Best match must beat the runner-up by at least this much (when multiple people enrolled). */
    const val MIN_MATCH_MARGIN = 0.025f

    const val ENROLLMENT_SAMPLES = 5

    enum class ExtractMode {
        ENROLL,
        MATCH,
    }

    fun extract(face: Face, mode: ExtractMode = ExtractMode.MATCH): FloatArray? {
        // Use the same pose bounds for enroll and check-in so stored templates match live probes.
        val maxYaw = 18f
        val maxPitch = 18f
        val minFaceWidth = if (mode == ExtractMode.MATCH) 100 else 120

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

    /** Stores every enrollment capture as its own template (better than a single average). */
    fun encodeAll(samples: List<FloatArray>): String {
        require(samples.isNotEmpty()) { "samples must not be empty" }
        val templates = samples.toMutableList()
        average(samples)?.let { templates.add(it) }
        if (templates.size == 1) return encode(templates.first())
        return SIGNATURE_MULTI_PREFIX + templates.joinToString(MULTI_SAMPLE_SEPARATOR) {
            it.joinToString(",")
        }
    }

    fun decode(raw: String): FloatArray? = decodeAll(raw).firstOrNull()

    fun decodeAll(raw: String): List<FloatArray> {
        if (raw.startsWith(SIGNATURE_MULTI_PREFIX)) {
            return raw.removePrefix(SIGNATURE_MULTI_PREFIX)
                .split(MULTI_SAMPLE_SEPARATOR)
                .mapNotNull { body ->
                    if (body.isBlank()) return@mapNotNull null
                    body.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray().takeIf { it.isNotEmpty() }
                }
        }
        val body = if (raw.startsWith(SIGNATURE_VERSION_PREFIX)) {
            raw.removePrefix(SIGNATURE_VERSION_PREFIX)
        } else {
            raw
        }
        if (body.isBlank()) return emptyList()
        val single = body.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray().takeIf { it.isNotEmpty() }
        return if (single == null) emptyList() else listOf(single)
    }

    fun isCurrentFormat(raw: String): Boolean =
        raw.startsWith(SIGNATURE_VERSION_PREFIX) || raw.startsWith(SIGNATURE_MULTI_PREFIX)

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
            .groupBy { it.first }
            .map { (name, entries) ->
                name to entries.minOf { distance(probe, it.second) }
            }
            .sortedBy { it.second }

        val (bestName, bestDistance) = ranked.first()
        val secondDistance = ranked.getOrNull(1)?.second ?: Float.MAX_VALUE

        if (bestDistance > MATCH_THRESHOLD) {
            return MatchResult(displayName = null, ambiguous = false)
        }
        if (ranked.size > 1 && (secondDistance - bestDistance) < MIN_MATCH_MARGIN) {
            return MatchResult(displayName = null, ambiguous = true)
        }
        return MatchResult(displayName = bestName, ambiguous = false)
    }

    private const val SIGNATURE_VERSION_PREFIX = "2|"
    private const val SIGNATURE_MULTI_PREFIX = "2m|"
    private const val MULTI_SAMPLE_SEPARATOR = "||"
}
