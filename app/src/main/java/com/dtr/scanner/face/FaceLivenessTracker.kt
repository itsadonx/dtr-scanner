package com.dtr.scanner.face

import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs

/**
 * Presentation-attack checks: blink plus optional head motion for enrollment.
 * Check-in uses [Mode.CHECK_IN] (blink only); registration uses [Mode.ENROLLMENT].
 */
class FaceLivenessTracker {

    enum class Mode {
        /** Attendance scan: blink once, no head-turn challenge. */
        CHECK_IN,
        /** Enrollment: blink, turn left/right, nod up/down. */
        ENROLLMENT,
    }

    var mode: Mode = Mode.CHECK_IN

    private var sawEyesOpen = false
    private var sawEyesClosed = false
    private var sawBlink = false
    private var referenceYaw: Float? = null
    private var referencePitch: Float? = null
    private var minHeadYaw = Float.MAX_VALUE
    private var maxHeadYaw = Float.MIN_VALUE
    private var minHeadPitch = Float.MAX_VALUE
    private var maxHeadPitch = Float.MIN_VALUE
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
        referencePitch = null
        minHeadYaw = Float.MAX_VALUE
        maxHeadYaw = Float.MIN_VALUE
        minHeadPitch = Float.MAX_VALUE
        maxHeadPitch = Float.MIN_VALUE
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
        if (mode == Mode.ENROLLMENT) {
            updateHeadMotion(face)
        }

        val challengeOk = when (mode) {
            Mode.CHECK_IN -> sawBlink
            Mode.ENROLLMENT -> sawBlink && hasEnrollmentHeadChallenge()
        }

        if (challengeOk) {
            verifiedUntilMs = nowMs + VERIFICATION_WINDOW_MS
            return LivenessUpdate(
                hint = "Live check passed — matching face…",
                verified = true,
                rejectedPhoto = false,
            )
        }

        val hint = when (mode) {
            Mode.CHECK_IN -> when {
                !sawBlink -> "Blink once so we know you're live (not a photo)."
                else -> "Verifying…"
            }

            Mode.ENROLLMENT -> when {
                !sawBlink -> "Blink once, then move your head to enroll."
                !hasYawChallenge() -> "Slowly turn your head left, then right."
                !hasPitchChallenge() -> "Slowly nod up, then down."
                else -> "Verifying…"
            }
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

    private fun hasEnrollmentHeadChallenge(): Boolean = hasYawChallenge() && hasPitchChallenge()

    private fun hasYawChallenge(): Boolean {
        val ref = referenceYaw ?: return false
        return minHeadYaw <= ref - MIN_YAW_EACH_SIDE_DEGREES &&
            maxHeadYaw >= ref + MIN_YAW_EACH_SIDE_DEGREES
    }

    private fun hasPitchChallenge(): Boolean {
        val ref = referencePitch ?: return false
        return minHeadPitch <= ref - MIN_PITCH_EACH_SIDE_DEGREES &&
            maxHeadPitch >= ref + MIN_PITCH_EACH_SIDE_DEGREES
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
            if (referencePitch == null) {
                referencePitch = face.headEulerAngleX
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
        val pitch = face.headEulerAngleX
        minHeadYaw = minOf(minHeadYaw, yaw)
        maxHeadYaw = maxOf(maxHeadYaw, yaw)
        minHeadPitch = minOf(minHeadPitch, pitch)
        maxHeadPitch = maxOf(maxHeadPitch, pitch)
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
        private const val MIN_PITCH_EACH_SIDE_DEGREES = 6f
        private const val STATIC_MOVE_PX = 1.25f
        private const val EYE_DELTA_RESET = 0.08f
        private const val YAW_DELTA_RESET_DEG = 0.45f
        private const val STATIC_FRAME_LIMIT = 45
        private const val VERIFICATION_WINDOW_MS = 5_000L
        private const val PHOTO_REJECT_COOLDOWN_MS = 2_500L
    }
}
