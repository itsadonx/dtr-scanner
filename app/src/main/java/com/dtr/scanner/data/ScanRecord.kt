package com.dtr.scanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_records")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val scannedAtEpochMs: Long,
    val scanType: String = ScanType.QR,
)
