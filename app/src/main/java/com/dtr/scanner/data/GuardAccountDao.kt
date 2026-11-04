package com.dtr.scanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GuardAccountDao {
    @Query("SELECT * FROM guard_accounts WHERE lower(guardId) = lower(:guardId) LIMIT 1")
    suspend fun findByGuardId(guardId: String): GuardAccount?

    @Query("SELECT COUNT(*) FROM guard_accounts")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<GuardAccount>)

    @Query("DELETE FROM guard_accounts")
    suspend fun deleteAll()
}
