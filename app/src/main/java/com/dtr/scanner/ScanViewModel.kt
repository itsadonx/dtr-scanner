package com.dtr.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dtr.scanner.data.AppDatabase
import com.dtr.scanner.data.FaceEnrollmentInput
import com.dtr.scanner.data.FaceProfile
import com.dtr.scanner.data.ScanRecord
import com.dtr.scanner.data.ScanType
import com.dtr.scanner.face.FaceSignature
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val scanDao = database.scanDao()
    private val faceDao = database.faceProfileDao()

    val records = scanDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val faceProfiles = faceDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @Volatile
    private var lastFaceSaveMs: Long = 0L

    @Volatile
    private var lastFaceName: String? = null

    fun saveScan(content: String, scanType: String = ScanType.QR) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            scanDao.insert(
                ScanRecord(
                    content = trimmed,
                    scannedAtEpochMs = System.currentTimeMillis(),
                    scanType = scanType,
                ),
            )
        }
    }

    /** Returns true when a new face attendance row was saved. */
    fun saveFaceRecognition(displayName: String): Boolean {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return false
        val now = System.currentTimeMillis()
        if (trimmed == lastFaceName && now - lastFaceSaveMs < FACE_DUPLICATE_COOLDOWN_MS) {
            return false
        }
        lastFaceName = trimmed
        lastFaceSaveMs = now
        saveScan("Face check-in: $trimmed", ScanType.FACE)
        return true
    }

    fun enrollFace(
        input: FaceEnrollmentInput,
        onSaved: (FaceProfile) -> Unit = {},
    ) {
        val trimmedName = input.fullName.trim()
        if (trimmedName.isEmpty() || input.signatures.isEmpty()) return
        viewModelScope.launch {
            val profile = FaceProfile(
                fullName = trimmedName,
                area = input.area.trim(),
                businessUnit = input.businessUnit.trim(),
                signature = FaceSignature.encodeAll(input.signatures),
                enrolledAtEpochMs = System.currentTimeMillis(),
            )
            val id = faceDao.insert(profile)
            onSaved(profile.copy(id = id))
        }
    }

    fun deleteFaceProfile(id: Long) {
        viewModelScope.launch {
            faceDao.deleteById(id)
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            scanDao.deleteById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            scanDao.deleteAll()
        }
    }

    companion object {
        private const val FACE_DUPLICATE_COOLDOWN_MS = 2_500L
    }
}
