package com.dtr.scanner.face

import com.dtr.scanner.data.FaceProfile

/** One template belonging to a registered person (used at check-in). */
data class EnrolledFaceTemplate(
    val profileId: Long,
    val displayName: String,
    val template: FloatArray,
)

/** Capture templates used for identification (excludes the blended enrollment average). */
fun FaceProfile.templatesForMatching(): List<FloatArray> {
    if (FaceSignature.isLegacyFormat(signature)) return emptyList()
    val decoded = FaceSignature.decodeAll(signature).filter {
        it.size == FaceSignature.signatureFloatCount ||
            it.size == FaceSignature.v5SignatureFloatCount
    }
    return if (decoded.size > FaceSignature.ENROLLMENT_SAMPLES) {
        decoded.dropLast(1)
    } else {
        decoded
    }
}

fun FaceProfile.toEnrolledTemplates(): List<EnrolledFaceTemplate> =
    templatesForMatching().map { template ->
        EnrolledFaceTemplate(
            profileId = id,
            displayName = fullName,
            template = template,
        )
    }

fun List<FaceProfile>.toMatchCatalog(): List<EnrolledFaceTemplate> =
    flatMap { it.toEnrolledTemplates() }
