package com.dtr.scanner.face

import com.dtr.scanner.data.ScanRecord
import com.dtr.scanner.data.ScanType

object FaceCheckInPolicy {
    const val DUPLICATE_WINDOW_MS = 60_000L

    fun recordContent(displayName: String): String = "Face check-in: ${displayName.trim()}"

    fun isDuplicateInRecords(records: List<ScanRecord>, displayName: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return false
        val since = nowMs - DUPLICATE_WINDOW_MS
        val content = recordContent(trimmed)
        return records.any {
            it.scanType == ScanType.FACE &&
                it.content == content &&
                it.scannedAtEpochMs >= since
        }
    }
}
