package com.dtr.scanner.face

import com.dtr.scanner.data.FaceProfile

fun FaceProfile.matchTemplates(): List<Pair<String, FloatArray>> {
    if (FaceSignature.isLegacyFormat(signature)) return emptyList()
    return FaceSignature.decodeAll(signature)
        .filter { it.size == FaceSignature.signatureFloatCount }
        .map { fullName to it }
}

fun List<FaceProfile>.toMatchCatalog(): List<Pair<String, FloatArray>> =
    flatMap { it.matchTemplates() }
