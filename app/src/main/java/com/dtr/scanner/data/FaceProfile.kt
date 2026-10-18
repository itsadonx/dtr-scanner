package com.dtr.scanner.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "face_profiles")
data class FaceProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "displayName")
    val fullName: String,
    val area: String = "",
    val businessUnit: String = "",
    /** Comma-separated normalized landmark coordinates. */
    val signature: String,
    /** Absolute paths to saved JPEG face crops, separated by || */
    val templateImagePaths: String = "",
    val enrolledAtEpochMs: Long,
)
