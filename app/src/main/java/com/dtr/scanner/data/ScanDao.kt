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
}
