package com.dtr.scanner.face

import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

object FaceSignature {
    private val landmarkTypes = listOf(
        FaceLandmark.LEFT_EYE,
        FaceLandmark.RIGHT_EYE,
        FaceLandmark.NOSE_BASE,
        FaceLandmark.MOUTH_LEFT,
        FaceLandmark.MOUTH_RIGHT,
        FaceLandmark.LEFT_CHEEK,
        FaceLandmark.RIGHT_CHEEK,
    )

    /** Lower is a closer match. Typical matches are under [MATCH_THRESHOLD]. */
    const val MATCH_THRESHOLD = 0.24f

    fun extract(face: Face): FloatArray? {
        if (abs(face.headEulerAngleY) > 22f) return null
        if (abs(face.headEulerAngleX) > 18f) return null
        if (face.boundingBox.width() < 120) return null

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val centerX = (leftEye.x + rightEye.x) / 2f
        val centerY = (leftEye.y + rightEye.y) / 2f
        val scale = hypot(leftEye.x - rightEye.x, leftEye.y - rightEye.y).coerceAtLeast(1f)

        val values = mutableListOf<Float>()
        for (type in landmarkTypes) {
            val point = face.getLandmark(type)?.position ?: return null
            values.add((point.x - centerX) / scale)
            values.add((point.y - centerY) / scale)
        }
        return values.toFloatArray()
    }

    fun encode(values: FloatArray): String = values.joinToString(",")

    fun decode(raw: String): FloatArray? {
        if (raw.isBlank()) return null
        return raw.split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray().takeIf { it.isNotEmpty() }
    }

    fun distance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return Float.MAX_VALUE
        var sum = 0f
        for (i in a.indices) {
            val delta = a[i] - b[i]
            sum += delta * delta
        }
        return sqrt(sum / a.size)
    }

    fun findBestMatch(
        probe: FloatArray,
        profiles: List<Pair<Long, FloatArray>>,
    ): Pair<Long, Float>? {
        var bestId: Long? = null
        var bestDistance = Float.MAX_VALUE
        for ((id, enrolled) in profiles) {
            val distance = distance(probe, enrolled)
            if (distance < bestDistance) {
                bestDistance = distance
                bestId = id
            }
        }
        val id = bestId ?: return null
        if (bestDistance > MATCH_THRESHOLD) return null
        return id to bestDistance
    }
}
