package com.dtr.scanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_records ORDER BY scannedAtEpochMs DESC")
    fun observeAll(): Flow<List<ScanRecord>>

    @Insert
    suspend fun insert(record: ScanRecord): Long

    @Query("DELETE FROM scan_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scan_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM scan_records ORDER BY scannedAtEpochMs DESC")
    suspend fun getAllOnce(): List<ScanRecord>

    @Query("SELECT * FROM scan_records WHERE synced = 0")
    suspend fun unsynced(): List<ScanRecord>

    @Query("UPDATE scan_records SET clientId = :clientId WHERE id = :id")
    suspend fun setClientId(id: Long, clientId: String)

    @Query("UPDATE scan_records SET synced = 1 WHERE clientId IN (:clientIds)")
    suspend fun markSynced(clientIds: List<String>)
}
