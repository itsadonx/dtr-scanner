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
    const val MATCH_THRESHOLD = 0.34f

    /** Strong match — accept even if runner-up is close. */
    private const val STRONG_MATCH_THRESHOLD = 0.24f

    /** Best match must beat the runner-up by at least this much (when multiple people enrolled). */
    const val MIN_MATCH_MARGIN = 0.012f

    const val ENROLLMENT_SAMPLES = 5

    enum class ExtractMode {
        ENROLL,
        MATCH,
    }

    fun isFrontalForEnrollment(face: Face): Boolean {
        return abs(face.headEulerAngleY) <= 20f && abs(face.headEulerAngleX) <= 20f
    }

    val signatureFloatCount: Int get() = contourPointCounts.sum() * 2

    fun extract(face: Face, mode: ExtractMode = ExtractMode.MATCH): FloatArray? {
        // Use the same pose bounds for enroll and check-in so stored templates match live probes.
        val maxYaw = 22f
        val maxPitch = 22f
        val minFaceWidth = 64

        if (abs(face.headEulerAngleY) > maxYaw) return null
        if (abs(face.headEulerAngleX) > maxPitch) return null
        if (face.boundingBox.width() < minFaceWidth) return null

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val centerX = (leftEye.x + rightEye.x) / 2f
        val centerY = (leftEye.y + rightEye.y) / 2f
        val scale = hypot(leftEye.x - rightEye.x, leftEye.y - rightEye.y).coerceAtLeast(1f)

        val values = ArrayList<Float>(signatureFloatCount)
        for (index in contourTypes.indices) {
            appendContourPoints(
                face = face,
                type = contourTypes[index],
                expectedPoints = contourPointCounts[index],
                centerX = centerX,
                centerY = centerY,
                scale = scale,
                out = values,
            )
        }
        return values.toFloatArray()
    }

    private fun appendContourPoints(
        face: Face,
        type: Int,
        expectedPoints: Int,
        centerX: Float,
        centerY: Float,
        scale: Float,
        out: MutableList<Float>,
    ) {
        val raw = face.getContour(type)?.points.orEmpty()
        var used = 0
        for (point in raw) {
            if (used >= expectedPoints) break
            out.add((point.x - centerX) / scale)
            out.add((point.y - centerY) / scale)
            used++
        }
        repeat(expectedPoints - used) {
            out.add(0f)
            out.add(0f)
        }
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

    fun isCurrentFormat(raw: String): Boolean {
        if (raw.startsWith(SIGNATURE_MULTI_PREFIX)) return true
        if (!raw.startsWith(SIGNATURE_VERSION_PREFIX)) return false
        // Avoid treating multi templates (2m|) as legacy single — only "2|" not "2m|".
        return raw.length < 3 || raw[2] != 'm'
    }

    /**
     * Splits a flat signature back into ML Kit contour polylines (same order as [contourTypes]).
     */
    fun splitIntoContours(signature: FloatArray): List<List<NormPoint>> {
        if (signature.size < 4) return emptyList()
        val expectedFloats = contourPointCounts.sum() * 2
        if (signature.size != expectedFloats) {
            return listOf(signatureToPointsFallback(signature))
        }
        val contours = ArrayList<List<NormPoint>>(contourPointCounts.size)
        var index = 0
        for (count in contourPointCounts) {
            val points = ArrayList<NormPoint>(count)
            repeat(count) {
                points.add(NormPoint(signature[index], signature[index + 1]))
                index += 2
            }
            contours.add(points)
        }
        return contours
    }

    data class NormPoint(val x: Float, val y: Float)

    fun templateLabel(index: Int, totalTemplates: Int): String {
        return when {
            totalTemplates > ENROLLMENT_SAMPLES && index == totalTemplates - 1 -> "Blended average"
            else -> "Capture ${index + 1}"
        }
    }

    private fun signatureToPointsFallback(signature: FloatArray): List<NormPoint> {
        val points = ArrayList<NormPoint>(signature.size / 2)
        var i = 0
        while (i + 1 < signature.size) {
            points.add(NormPoint(signature[i], signature[i + 1]))
            i += 2
        }
        return points
    }

    fun distance(a: FloatArray, b: FloatArray): Float {
        val shared = minOf(a.size, b.size)
        if (shared < 32) return Float.MAX_VALUE
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
        if (used < 32) return Float.MAX_VALUE
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
            if (bestDistance <= MATCH_THRESHOLD * 0.85f) {
                return MatchResult(displayName = bestName, ambiguous = false)
            }
            return MatchResult(displayName = null, ambiguous = true)
        }
        return MatchResult(displayName = bestName, ambiguous = false)
    }

    /** Point counts per [contourTypes] entry (ML Kit face mesh). */
    private val contourPointCounts = listOf(
        36, // FACE
        5, 5, 5, 5, // eyebrows
        16, 16, // eyes
        2, 5, // nose
        11, 9, 9, 11, // lips
        1, 1, // cheeks
    )

    private const val SIGNATURE_VERSION_PREFIX = "2|"
    private const val SIGNATURE_MULTI_PREFIX = "2m|"
    private const val MULTI_SAMPLE_SEPARATOR = "||"
}
