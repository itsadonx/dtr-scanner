package com.dtr.scanner.face

import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

object FaceSignature {
    /** Eyes, nose, and mouth — stable on front/back camera without ear gaps. */
    private val landmarkTypes = intArrayOf(
        FaceLandmark.LEFT_EYE,
        FaceLandmark.RIGHT_EYE,
        FaceLandmark.NOSE_BASE,
        FaceLandmark.MOUTH_LEFT,
        FaceLandmark.MOUTH_RIGHT,
        FaceLandmark.MOUTH_BOTTOM,
    )

    /** Lower is a closer match. Same person is usually under [MATCH_THRESHOLD]. */
    const val MATCH_THRESHOLD = 0.28f

    private const val STRONG_MATCH_THRESHOLD = 0.15f
    const val MIN_MATCH_MARGIN = 0.008f

    const val ENROLLMENT_SAMPLES = 5

    enum class ExtractMode {
        ENROLL,
        MATCH,
    }

    fun isFrontalForEnrollment(face: Face): Boolean {
        return abs(face.headEulerAngleY) <= 22f && abs(face.headEulerAngleX) <= 22f
    }

    val signatureFloatCount: Int = landmarkTypes.size * 2

    fun extract(face: Face, mode: ExtractMode = ExtractMode.MATCH): FloatArray? {
        val maxYaw = 24f
        val maxPitch = 24f
        val minFaceWidth = 56

        if (abs(face.headEulerAngleY) > maxYaw) return null
        if (abs(face.headEulerAngleX) > maxPitch) return null
        if (face.boundingBox.width() < minFaceWidth) return null

        if (mode == ExtractMode.ENROLL && !isFrontalForEnrollment(face)) {
            return null
        }

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val centerX = (leftEye.x + rightEye.x) / 2f
        val centerY = (leftEye.y + rightEye.y) / 2f
        val scale = hypot(leftEye.x - rightEye.x, leftEye.y - rightEye.y).coerceAtLeast(1f)

        val values = FloatArray(signatureFloatCount)
        var index = 0
        for (type in landmarkTypes) {
            val pos = face.getLandmark(type)?.position ?: return null
            values[index] = (pos.x - centerX) / scale
            values[index + 1] = (pos.y - centerY) / scale
            index += 2
        }
        return values
    }

    fun encode(values: FloatArray): String = SIGNATURE_VERSION_PREFIX + values.joinToString(",")

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
        when {
            raw.startsWith(SIGNATURE_MULTI_PREFIX) -> {
                return raw.removePrefix(SIGNATURE_MULTI_PREFIX)
                    .split(MULTI_SAMPLE_SEPARATOR)
                    .mapNotNull { parseBody(it) }
            }

            raw.startsWith(SIGNATURE_VERSION_PREFIX) -> {
                return listOfNotNull(parseBody(raw.removePrefix(SIGNATURE_VERSION_PREFIX)))
            }

            raw.startsWith(LEGACY_MULTI_PREFIX) -> {
                return raw.removePrefix(LEGACY_MULTI_PREFIX)
                    .split(MULTI_SAMPLE_SEPARATOR)
                    .mapNotNull { parseBody(it) }
            }

            raw.startsWith(LEGACY_VERSION_PREFIX) -> {
                return listOfNotNull(parseBody(raw.removePrefix(LEGACY_VERSION_PREFIX)))
            }
        }
        return listOfNotNull(parseBody(raw))
    }

    fun isCurrentFormat(raw: String): Boolean =
        raw.startsWith(SIGNATURE_VERSION_PREFIX) ||
            raw.startsWith(SIGNATURE_MULTI_PREFIX)

    fun isLegacyFormat(raw: String): Boolean =
        raw.startsWith(LEGACY_VERSION_PREFIX) || raw.startsWith(LEGACY_MULTI_PREFIX)

    fun splitIntoContours(signature: FloatArray): List<List<NormPoint>> {
        return listOf(signatureToPoints(signature))
    }

    data class NormPoint(val x: Float, val y: Float)

    fun templateLabel(index: Int, totalTemplates: Int): String {
        return when {
            totalTemplates > ENROLLMENT_SAMPLES && index == totalTemplates - 1 -> "Blended average"
            else -> "Capture ${index + 1}"
        }
    }

    private fun signatureToPoints(signature: FloatArray): List<NormPoint> {
        val points = ArrayList<NormPoint>(signature.size / 2)
        var i = 0
        while (i + 1 < signature.size) {
            points.add(NormPoint(signature[i], signature[i + 1]))
            i += 2
        }
        return points
    }

    private fun parseBody(body: String): FloatArray? {
        if (body.isBlank()) return null
        val values = body.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
        if (values.isEmpty()) return null
        if (values.size == signatureFloatCount) return values
        // Older v4 templates stored 8 landmarks (16 floats); ignore for matching.
        return null
    }

    fun distance(a: FloatArray, b: FloatArray): Float {
        val shared = minOf(a.size, b.size)
        if (shared < 10) return Float.MAX_VALUE
        var sum = 0f
        var used = 0
        var i = 0
        while (i + 1 < shared) {
            val ax = a[i]
            val ay = a[i + 1]
            val bx = b[i]
            val by = b[i + 1]
            if (ax == 0f && ay == 0f && bx == 0f && by == 0f) {
                i += 2
                continue
            }
            val dx = ax - bx
            val dy = ay - by
            sum += dx * dx + dy * dy
            used += 2
            i += 2
        }
        if (used < 10) return Float.MAX_VALUE
        return sqrt(sum / used)
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
        if (ranked.size == 1) {
            return MatchResult(displayName = bestName, ambiguous = false)
        }
        val margin = secondDistance - bestDistance
        if (margin < MIN_MATCH_MARGIN && bestDistance > STRONG_MATCH_THRESHOLD) {
            return MatchResult(displayName = null, ambiguous = true)
        }
        return MatchResult(displayName = bestName, ambiguous = false)
    }

    private const val SIGNATURE_VERSION_PREFIX = "4|"
    private const val SIGNATURE_MULTI_PREFIX = "4m|"
    private const val LEGACY_VERSION_PREFIX = "2|"
    private const val LEGACY_MULTI_PREFIX = "2m|"
    private const val MULTI_SAMPLE_SEPARATOR = "||"
}
