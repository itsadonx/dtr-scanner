package com.dtr.scanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FaceProfileDao {
    @Query("SELECT * FROM face_profiles ORDER BY displayName ASC")
    fun observeAll(): Flow<List<FaceProfile>>

    @Insert
    suspend fun insert(profile: FaceProfile): Long

    @Query("DELETE FROM face_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM face_profiles")
    suspend fun deleteAll()

    @Query("SELECT id FROM face_profiles")
    suspend fun listAllIds(): List<Long>

    @Query("SELECT * FROM face_profiles ORDER BY displayName ASC")
    suspend fun getAllOnce(): List<FaceProfile>

    @Query("UPDATE face_profiles SET templateImagePaths = :paths WHERE id = :id")
    suspend fun updateTemplatePaths(id: Long, paths: String)
}
