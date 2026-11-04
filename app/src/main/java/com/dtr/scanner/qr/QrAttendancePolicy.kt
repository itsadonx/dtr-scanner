package com.dtr.scanner.qr

import com.dtr.scanner.data.AttendanceDirection
import com.dtr.scanner.data.ScanRecord
import com.dtr.scanner.data.ScanType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object QrAttendancePolicy {
    const val DUPLICATE_WINDOW_MS = 60_000L

    fun nextDirection(
        records: List<ScanRecord>,
        qrContent: String,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val last = lastQrScanToday(records, qrContent, nowMs, zone) ?: return AttendanceDirection.IN
        return if (AttendanceDirection.isOut(last.attendanceDirection)) {
            AttendanceDirection.IN
        } else {
            AttendanceDirection.OUT
        }
    }

    fun isDuplicate(
        records: List<ScanRecord>,
        qrContent: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val trimmed = qrContent.trim()
        if (trimmed.isEmpty()) return false
        val since = nowMs - DUPLICATE_WINDOW_MS
        return records.any {
            it.scanType == ScanType.QR &&
                it.content.trim() == trimmed &&
                it.scannedAtEpochMs >= since
        }
    }

    private fun lastQrScanToday(
        records: List<ScanRecord>,
        qrContent: String,
        nowMs: Long,
        zone: ZoneId,
    ): ScanRecord? {
        val trimmed = qrContent.trim()
        if (trimmed.isEmpty()) return null
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return records
            .asSequence()
            .filter {
                it.scanType == ScanType.QR &&
                    it.content.trim() == trimmed &&
                    localDate(it.scannedAtEpochMs, zone) == today
            }
            .maxByOrNull { it.scannedAtEpochMs }
    }

    private fun localDate(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
