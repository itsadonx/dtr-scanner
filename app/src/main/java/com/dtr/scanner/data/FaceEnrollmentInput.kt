package com.dtr.scanner.data

data class FaceEnrollmentInput(
    val fullName: String,
    val area: String,
    val businessUnit: String,
    val signatures: List<FloatArray>,
)
