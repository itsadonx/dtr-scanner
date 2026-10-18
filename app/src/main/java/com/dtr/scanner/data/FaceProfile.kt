package com.dtr.scanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_profiles")
data class FaceProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    /** Comma-separated normalized landmark coordinates. */
    val signature: String,
    val enrolledAtEpochMs: Long,
)
