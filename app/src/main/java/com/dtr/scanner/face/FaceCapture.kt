package com.dtr.scanner.face

data class FaceCapture(
    val signature: FloatArray,
    val thumbnailJpeg: ByteArray?,
    /** Extra probe vectors (e.g. v4 + v5) for check-in when the catalog mixes formats. */
    val matchProbes: List<FloatArray> = emptyList(),
) {
    fun probesForMatching(): List<FloatArray> =
        if (matchProbes.isEmpty()) listOf(signature) else matchProbes

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FaceCapture
        if (!signature.contentEquals(other.signature)) return false
        if (thumbnailJpeg != null) {
            if (other.thumbnailJpeg == null) return false
            if (!thumbnailJpeg.contentEquals(other.thumbnailJpeg)) return false
        } else if (other.thumbnailJpeg != null) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = signature.contentHashCode()
        result = 31 * result + (thumbnailJpeg?.contentHashCode() ?: 0)
        return result
    }
}
