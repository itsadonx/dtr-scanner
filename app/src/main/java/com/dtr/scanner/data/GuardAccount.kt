package com.dtr.scanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "guard_accounts")
data class GuardAccount(
    @PrimaryKey val id: String,
    val guardId: String,
    val displayName: String,
    val passwordSalt: String,
    val passwordHash: String,
    val active: Boolean,
    val updatedAtEpochMs: Long,
)
