package com.dtr.scanner.face

enum class EnrollmentGuideStep {
    BLINK,
    TURN_LEFT,
    TURN_RIGHT,
    NOD_UP,
    NOD_DOWN,
}

enum class EnrollmentStepStatus {
    PENDING,
    ACTIVE,
    DONE,
}

data class EnrollmentGuideState(
    val steps: List<EnrollmentStepState>,
    val activeStep: EnrollmentGuideStep?,
    val celebrateStep: EnrollmentGuideStep?,
    val livenessComplete: Boolean,
) {
    data class EnrollmentStepState(
        val step: EnrollmentGuideStep,
        val status: EnrollmentStepStatus,
    )

    companion object {
        fun initial(): EnrollmentGuideState = EnrollmentGuideState(
            steps = EnrollmentGuideStep.entries.map {
                EnrollmentStepState(
                    step = it,
                    status = if (it == EnrollmentGuideStep.BLINK) {
                        EnrollmentStepStatus.ACTIVE
                    } else {
                        EnrollmentStepStatus.PENDING
                    },
                )
            },
            activeStep = EnrollmentGuideStep.BLINK,
            celebrateStep = null,
            livenessComplete = false,
        )
    }
}
