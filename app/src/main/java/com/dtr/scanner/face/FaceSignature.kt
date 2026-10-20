package com.dtr.scanner.face
import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
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
    private data class ContourSampleSpec(val type: Int, val pointCount: Int)
    /** Dense contour samples (v5) for much better separation between similar faces. */
    private val v5ContourSpecs = listOf(
        ContourSampleSpec(FaceContour.LEFT_EYE, 8),
        ContourSampleSpec(FaceContour.RIGHT_EYE, 8),
        ContourSampleSpec(FaceContour.NOSE_BRIDGE, 6),
        ContourSampleSpec(FaceContour.NOSE_BOTTOM, 4),
        ContourSampleSpec(FaceContour.UPPER_LIP_TOP, 8),
        ContourSampleSpec(FaceContour.LOWER_LIP_BOTTOM, 8),
        ContourSampleSpec(FaceContour.FACE, 24),
    )
    /** Lower is a closer match. Same person is usually under the threshold for their format. */
    const val MATCH_THRESHOLD = 0.10f
    private const val MATCH_THRESHOLD_V5 = 0.085f
    /** Exceptionally close match; still requires separation when multiple people are enrolled. */
    private const val STRONG_MATCH_THRESHOLD = 0.075f
    private const val STRONG_MATCH_THRESHOLD_V5 = 0.068f
    /** Minimum score gap between 1st and 2nd person to avoid mis-identification. */
    const val MIN_MATCH_MARGIN = 0.09f
    /** Extra separation required when the best match is not a strong match. */
    private const val MIN_MARGIN_WHEN_WEAK = 0.12f
    /** Minimum gap between the best and second person's closest template distances. */
    private const val MIN_BEST_TEMPLATE_GAP = 0.038f
    /** Max landmark distance between consecutive enrollment samples for one person. */
    const val ENROLL_SAMPLE_MAX_DISTANCE = 0.12f
    const val ENROLLMENT_SAMPLES = 5
    /** Per-landmark pair weights (eyes/nose weighted higher than mouth). */
    private val landmarkPairWeights = floatArrayOf(
        1.45f,
        1.45f,
        1.55f,
        1.0f,
        1.0f,
        1.05f,
    )
    enum class ExtractMode {
        ENROLL,
        MATCH,
    }
    fun isFrontalForEnrollment(face: Face): Boolean {
        return abs(face.headEulerAngleY) <= 18f && abs(face.headEulerAngleX) <= 18f
    }
    fun isFrontalForMatch(face: Face): Boolean {
        return abs(face.headEulerAngleY) <= 12f && abs(face.headEulerAngleX) <= 12f
    }
    fun hasOpenEyes(face: Face): Boolean {
        val left = face.leftEyeOpenProbability ?: return true
        val right = face.rightEyeOpenProbability ?: return true
        return left >= 0.6f && right >= 0.6f
    }
    val signatureFloatCount: Int = landmarkTypes.size * 2
    val v5SignatureFloatCount: Int =
        signatureFloatCount + v5ContourSpecs.sumOf { it.pointCount * 2 }
    fun extract(
        face: Face,
        mode: ExtractMode = ExtractMode.MATCH,
        matchTemplateFloatCount: Int? = null,
    ): FloatArray? {
        val minFaceWidth = when (mode) {
            ExtractMode.MATCH -> 112
            ExtractMode.ENROLL -> 72
        }
        if (face.boundingBox.width() < minFaceWidth) return null
        when (mode) {
            ExtractMode.MATCH -> {
                if (!isFrontalForMatch(face) || !hasOpenEyes(face)) return null
            }
            ExtractMode.ENROLL -> {
                if (!isFrontalForEnrollment(face)) return null
            }
        }
        if (mode == ExtractMode.ENROLL) {
            return extractV5(face)
        }
        return when (matchTemplateFloatCount) {
            v5SignatureFloatCount -> extractV5(face)
            signatureFloatCount -> extractLandmarksOnly(face)
            else -> extractV5(face) ?: extractLandmarksOnly(face)
        }
    }
    private fun extractLandmarksOnly(face: Face): FloatArray? {
        val frame = normalizeFrame(face) ?: return null
        val values = FloatArray(signatureFloatCount)
        var index = 0
        for (type in landmarkTypes) {
            val pos = face.getLandmark(type)?.position ?: return null
            values[index] = frame.normalizeX(pos.x)
            values[index + 1] = frame.normalizeY(pos.y)
            index += 2
        }
        return values
    }
    private fun extractV5(face: Face): FloatArray? {
        val frame = normalizeFrame(face) ?: return null
        val values = ArrayList<Float>(v5SignatureFloatCount)
        for (type in landmarkTypes) {
            val pos = face.getLandmark(type)?.position ?: return null
            values.add(frame.normalizeX(pos.x))
            values.add(frame.normalizeY(pos.y))
        }
        for (spec in v5ContourSpecs) {
            val contour = face.getContour(spec.type) ?: return null
            val sampled = resampleContour(contour.points, spec.pointCount)
            if (sampled.size < spec.pointCount) return null
            for (point in sampled) {
                values.add(frame.normalizeX(point.x))
                values.add(frame.normalizeY(point.y))
            }
        }
        return if (values.size == v5SignatureFloatCount) {
            values.toFloatArray()
        } else {
            null
        }
    }
    private data class NormalizedFrame(
        val centerX: Float,
        val centerY: Float,
        val scale: Float,
    ) {
        fun normalizeX(x: Float): Float = (x - centerX) / scale
        fun normalizeY(y: Float): Float = (y - centerY) / scale
    }
    private fun normalizeFrame(face: Face): NormalizedFrame? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val centerX = (leftEye.x + rightEye.x) / 2f
        val centerY = (leftEye.y + rightEye.y) / 2f
        val scale = hypot(leftEye.x - rightEye.x, leftEye.y - rightEye.y).coerceAtLeast(1f)
        return NormalizedFrame(centerX, centerY, scale)
    }
    private fun resampleContour(points: List<PointF>, targetCount: Int): List<PointF> {
        if (points.isEmpty() || targetCount <= 0) return emptyList()
        if (points.size == 1) {
            return List(targetCount) { points[0] }
        }
        val cumulative = FloatArray(points.size)
        cumulative[0] = 0f
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            cumulative[i] = cumulative[i - 1] + hypot(curr.x - prev.x, curr.y - prev.y)
        }
        val total = cumulative.last().coerceAtLeast(1e-3f)
        val result = ArrayList<PointF>(targetCount)
        var segment = 0
        for (sample in 0 until targetCount) {
            val targetLength = total * sample / (targetCount - 1).coerceAtLeast(1)
            while (segment + 1 < cumulative.size && cumulative[segment + 1] < targetLength) {
                segment++
            }
            val start = points[segment]
            val end = points[(segment + 1).coerceAtMost(points.lastIndex)]
            val span = (cumulative[(segment + 1).coerceAtMost(points.lastIndex)] - cumulative[segment])
                .coerceAtLeast(1e-3f)
            val t = ((targetLength - cumulative[segment]) / span).coerceIn(0f, 1f)
            result.add(
                PointF(
                    start.x + t * (end.x - start.x),
                    start.y + t * (end.y - start.y),
                ),
            )
        }
        return result
    }
    fun encode(values: FloatArray): String {
        val prefix = when (values.size) {
            v5SignatureFloatCount -> SIGNATURE_V5_PREFIX
            signatureFloatCount -> SIGNATURE_VERSION_PREFIX
            else -> SIGNATURE_VERSION_PREFIX
        }
        return prefix + values.joinToString(",")
    }
    fun encodeAll(samples: List<FloatArray>): String {
        require(samples.isNotEmpty()) { "samples must not be empty" }
        val templates = samples.toMutableList()
        average(samples)?.let { templates.add(it) }
        val multiPrefix = when (templates.first().size) {
            v5SignatureFloatCount -> SIGNATURE_V5_MULTI_PREFIX
            else -> SIGNATURE_MULTI_PREFIX
        }
        if (templates.size == 1) return encode(templates.first())
        return multiPrefix + templates.joinToString(MULTI_SAMPLE_SEPARATOR) {
            it.joinToString(",")
        }
    }
    fun decode(raw: String): FloatArray? = decodeAll(raw).firstOrNull()
    fun decodeAll(raw: String): List<FloatArray> {
        when {
            raw.startsWith(SIGNATURE_V5_MULTI_PREFIX) -> {
                return raw.removePrefix(SIGNATURE_V5_MULTI_PREFIX)
                    .split(MULTI_SAMPLE_SEPARATOR)
                    .mapNotNull { parseBody(it) }
            }
            raw.startsWith(SIGNATURE_V5_PREFIX) -> {
                return listOfNotNull(parseBody(raw.removePrefix(SIGNATURE_V5_PREFIX)))
            }
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
            raw.startsWith(SIGNATURE_MULTI_PREFIX) ||
            raw.startsWith(SIGNATURE_V5_PREFIX) ||
            raw.startsWith(SIGNATURE_V5_MULTI_PREFIX)
    /** v5 contour templates — recommended for attendance accuracy. */
    fun isPreferredFormat(raw: String): Boolean =
        raw.startsWith(SIGNATURE_V5_PREFIX) || raw.startsWith(SIGNATURE_V5_MULTI_PREFIX)
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
        if (values.size == signatureFloatCount || values.size == v5SignatureFloatCount) return values
        return null
    }
    fun distance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return Float.MAX_VALUE
        val shared = a.size
        if (shared < 10) return Float.MAX_VALUE
        var sum = 0f
        var weightTotal = 0f
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
            val pairIndex = i / 2
            val pairWeight = when {
                pairIndex < landmarkPairWeights.size -> landmarkPairWeights[pairIndex]
                pairIndex < landmarkPairWeights.size + 16 -> 1.35f
                else -> 1.05f
            }
            sum += pairWeight * (dx * dx + dy * dy)
            weightTotal += pairWeight * 2f
            i += 2
        }
        if (weightTotal < 10f) return Float.MAX_VALUE
        return sqrt(sum / weightTotal)
    }
    fun average(samples: List<FloatArray>): FloatArray? {
        if (samples.isEmpty()) return null
        val size = samples.first().size
        if (samples.any { it.size != size }) return null
        return FloatArray(size) { index ->
            samples.map { it[index] }.average().toFloat()
        }
    }
    data class MatchCandidate(
        val profileId: Long,
        val displayName: String,
        val template: FloatArray,
    )
    data class MatchResult(
        val displayName: String?,
        val profileId: Long? = null,
        val ambiguous: Boolean,
    )
    private data class PersonScore(
        val profileId: Long,
        val name: String,
        val score: Float,
        val minDistance: Float,
    )
    fun match(
        probe: FloatArray,
        candidates: List<MatchCandidate>,
    ): MatchResult {
        val compatible = candidates.filter { it.template.size == probe.size }
        if (compatible.isEmpty()) {
            return MatchResult(displayName = null, ambiguous = false)
        }
        val matchThreshold = if (probe.size == v5SignatureFloatCount) {
            MATCH_THRESHOLD_V5
        } else {
            MATCH_THRESHOLD
        }
        val strongThreshold = if (probe.size == v5SignatureFloatCount) {
            STRONG_MATCH_THRESHOLD_V5
        } else {
            STRONG_MATCH_THRESHOLD
        }
        val ranked = compatible
            .groupBy { it.profileId }
            .map { (profileId, entries) ->
                val name = entries.first().displayName
                val distances = entries.map { distance(probe, it.template) }.sorted()
                val minDist = distances.first()
                val medianDist = distances[distances.size / 2]
                val score = minDist * 0.55f + medianDist * 0.45f
                PersonScore(profileId, name, score, minDist)
            }
            .sortedWith(compareBy<PersonScore> { it.minDistance }.thenBy { it.score })
        val best = ranked.first()
        val second = ranked.getOrNull(1)
        if (best.minDistance > matchThreshold || best.score > matchThreshold) {
            return MatchResult(displayName = null, ambiguous = false)
        }
        if (ranked.size == 1) {
            return MatchResult(displayName = best.name, profileId = best.profileId, ambiguous = false)
        }
        val templateGap = (second?.minDistance ?: Float.MAX_VALUE) - best.minDistance
        if (templateGap < MIN_BEST_TEMPLATE_GAP) {
            return MatchResult(displayName = null, ambiguous = true)
        }
        val scoreMargin = (second?.score ?: Float.MAX_VALUE) - best.score
        if (scoreMargin < MIN_MATCH_MARGIN) {
            return MatchResult(displayName = null, ambiguous = true)
        }
        if (best.score > strongThreshold && scoreMargin < MIN_MARGIN_WHEN_WEAK) {
            return MatchResult(displayName = null, ambiguous = true)
        }
        return MatchResult(displayName = best.name, profileId = best.profileId, ambiguous = false)
    }
    private const val SIGNATURE_VERSION_PREFIX = "4|"
    private const val SIGNATURE_MULTI_PREFIX = "4m|"
    private const val SIGNATURE_V5_PREFIX = "5|"
    private const val SIGNATURE_V5_MULTI_PREFIX = "5m|"
    private const val LEGACY_VERSION_PREFIX = "2|"
    private const val LEGACY_MULTI_PREFIX = "2m|"
    private const val MULTI_SAMPLE_SEPARATOR = "||"
}
