package com.dtr.scanner.face

import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs

/**
 * Presentation-attack checks for face check-in: blink, left/right head turn,
 * and rejection of perfectly still faces (typical of a photo or screen).
 */
class FaceLivenessTracker {

    private var sawEyesOpen = false
    private var sawEyesClosed = false
    private var sawBlink = false
    private var referenceYaw: Float? = null
    private var minHeadYaw = Float.MAX_VALUE
    private var maxHeadYaw = Float.MIN_VALUE
    private var staticFrameCount = 0
    private var lastNoseX: Float? = null
    private var lastNoseY: Float? = null
    private var lastLeftEye: Float? = null
    private var lastRightEye: Float? = null
    private var lastYawSample: Float? = null
    private var verifiedUntilMs: Long = 0L
    private var photoRejectedUntilMs: Long = 0L

    fun isVerified(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs < verifiedUntilMs

    fun resetChallenge() {
        sawEyesOpen = false
        sawEyesClosed = false
        sawBlink = false
        referenceYaw = null
        minHeadYaw = Float.MAX_VALUE
        maxHeadYaw = Float.MIN_VALUE
        staticFrameCount = 0
        lastNoseX = null
        lastNoseY = null
        lastLeftEye = null
        lastRightEye = null
        lastYawSample = null
        verifiedUntilMs = 0L
    }

    fun process(face: Face, nowMs: Long = System.currentTimeMillis()): LivenessUpdate {
        if (nowMs < photoRejectedUntilMs) {
            return LivenessUpdate(
                hint = "Live face only — use your real face, not a photo or screen.",
                verified = false,
                rejectedPhoto = true,
            )
        }

        if (isVerified(nowMs)) {
            return LivenessUpdate(
                hint = "Live check passed — matching face…",
                verified = true,
                rejectedPhoto = false,
            )
        }

        detectStaticPhoto(face)
        if (staticFrameCount >= STATIC_FRAME_LIMIT && !sawBlink) {
            resetChallenge()
            photoRejectedUntilMs = nowMs + PHOTO_REJECT_COOLDOWN_MS
            return LivenessUpdate(
                hint = "Live face only — photos and still images aren't allowed.",
                verified = false,
                rejectedPhoto = true,
            )
        }

        updateBlink(face)
        updateHeadMotion(face)

        val motionOk = hasHeadTurnChallenge()
        if (sawBlink && motionOk) {
            verifiedUntilMs = nowMs + VERIFICATION_WINDOW_MS
            return LivenessUpdate(
                hint = "Live check passed — matching face…",
                verified = true,
                rejectedPhoto = false,
            )
        }

        val hint = when {
            !sawBlink -> "Blink once so we know you're live (not a photo)."
            !motionOk -> "Slowly turn your head left, then right."
            else -> "Verifying…"
        }
        return LivenessUpdate(
            hint = hint,
            verified = false,
            rejectedPhoto = false,
        )
    }

    fun consumeVerification() {
        verifiedUntilMs = 0L
        resetChallenge()
    }

    private fun hasHeadTurnChallenge(): Boolean {
        val ref = referenceYaw ?: return false
        return minHeadYaw <= ref - MIN_YAW_EACH_SIDE_DEGREES &&
            maxHeadYaw >= ref + MIN_YAW_EACH_SIDE_DEGREES
    }

    private fun detectStaticPhoto(face: Face) {
        val left = face.leftEyeOpenProbability
        val right = face.rightEyeOpenProbability
        if (left != null && lastLeftEye != null && abs(left - lastLeftEye!!) > EYE_DELTA_RESET) {
            staticFrameCount = 0
        }
        if (right != null && lastRightEye != null && abs(right - lastRightEye!!) > EYE_DELTA_RESET) {
            staticFrameCount = 0
        }
        lastLeftEye = left
        lastRightEye = right

        val yaw = face.headEulerAngleY
        if (lastYawSample != null && abs(yaw - lastYawSample!!) > YAW_DELTA_RESET_DEG) {
            staticFrameCount = 0
        }
        lastYawSample = yaw

        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        if (nose == null) {
            staticFrameCount = 0
            return
        }
        val prevX = lastNoseX
        val prevY = lastNoseY
        lastNoseX = nose.x
        lastNoseY = nose.y
        if (prevX == null || prevY == null) {
            return
        }
        val moved = abs(nose.x - prevX) > STATIC_MOVE_PX || abs(nose.y - prevY) > STATIC_MOVE_PX
        if (moved || sawBlink) {
            staticFrameCount = 0
        } else {
            staticFrameCount++
        }
    }

    private fun updateBlink(face: Face) {
        val left = face.leftEyeOpenProbability ?: return
        val right = face.rightEyeOpenProbability ?: return
        val eyesOpen = left >= EYE_OPEN_MIN && right >= EYE_OPEN_MIN
        val eyesClosed = left <= EYE_CLOSED_MAX && right <= EYE_CLOSED_MAX
        if (eyesOpen) {
            sawEyesOpen = true
            if (referenceYaw == null) {
                referenceYaw = face.headEulerAngleY
            }
        }
        if (sawEyesOpen && eyesClosed) {
            sawEyesClosed = true
        }
        if (sawEyesClosed && eyesOpen) {
            sawBlink = true
        }
    }

    private fun updateHeadMotion(face: Face) {
        val yaw = face.headEulerAngleY
        minHeadYaw = minOf(minHeadYaw, yaw)
        maxHeadYaw = maxOf(maxHeadYaw, yaw)
    }

    data class LivenessUpdate(
        val hint: String,
        val verified: Boolean,
        val rejectedPhoto: Boolean,
    )

    companion object {
        private const val EYE_OPEN_MIN = 0.65f
        private const val EYE_CLOSED_MAX = 0.35f
        private const val MIN_YAW_EACH_SIDE_DEGREES = 7f
        private const val STATIC_MOVE_PX = 1.25f
        private const val EYE_DELTA_RESET = 0.08f
        private const val YAW_DELTA_RESET_DEG = 0.45f
        private const val STATIC_FRAME_LIMIT = 45
        private const val VERIFICATION_WINDOW_MS = 5_000L
        private const val PHOTO_REJECT_COOLDOWN_MS = 2_500L
    }
}
