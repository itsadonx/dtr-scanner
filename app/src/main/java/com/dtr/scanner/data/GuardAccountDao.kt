package com.dtr.scanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GuardAccountDao {
    @Query("SELECT * FROM guard_accounts WHERE lower(guardId) = lower(:guardId) LIMIT 1")
    suspend fun findByGuardId(guardId: String): GuardAccount?

    @Query("SELECT * FROM guard_accounts WHERE lower(displayName) = lower(:displayName) LIMIT 1")
    suspend fun findByDisplayName(displayName: String): GuardAccount?

    @Query("UPDATE guard_accounts SET passwordSalt = :salt, passwordHash = :hash, updatedAtEpochMs = :updated WHERE id = :id")
    suspend fun updatePassword(id: String, salt: String, hash: String, updated: Long)

    @Query("SELECT COUNT(*) FROM guard_accounts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<GuardAccount>)

    @Query("DELETE FROM guard_accounts")
    suspend fun deleteAll()
}
