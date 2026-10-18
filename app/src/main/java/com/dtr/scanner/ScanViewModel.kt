package com.dtr.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dtr.scanner.data.AppDatabase
import com.dtr.scanner.data.FaceEnrollmentInput
import com.dtr.scanner.data.FaceProfile
import com.dtr.scanner.data.FaceTemplateStore
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

    private val recentFaceCheckIns = mutableMapOf<String, Long>()

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

    fun isFaceCheckInDuplicate(displayName: String): Boolean {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return false
        pruneExpiredFaceCheckIns()
        val lastMs = recentFaceCheckIns[trimmed] ?: return false
        return System.currentTimeMillis() - lastMs < FACE_DUPLICATE_COOLDOWN_MS
    }

    /** Returns true when a new face attendance row was saved. */
    fun saveFaceRecognition(displayName: String): Boolean {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return false
        if (isFaceCheckInDuplicate(trimmed)) {
            return false
        }
        recentFaceCheckIns[trimmed] = System.currentTimeMillis()
        pruneExpiredFaceCheckIns()
        saveScan("Face check-in: $trimmed", ScanType.FACE)
        return true
    }

    private fun pruneExpiredFaceCheckIns() {
        val now = System.currentTimeMillis()
        val expired = recentFaceCheckIns.filterValues { now - it >= FACE_DUPLICATE_COOLDOWN_MS }.keys
        expired.forEach { recentFaceCheckIns.remove(it) }
    }

    fun enrollFace(
        input: FaceEnrollmentInput,
        onSaved: (FaceProfile) -> Unit = {},
    ) {
        val trimmedName = input.fullName.trim()
        if (trimmedName.isEmpty() || input.signatures.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val profile = FaceProfile(
                fullName = trimmedName,
                area = input.area.trim(),
                businessUnit = input.businessUnit.trim(),
                signature = FaceSignature.encodeAll(input.signatures),
                enrolledAtEpochMs = System.currentTimeMillis(),
            )
            val id = faceDao.insert(profile)
            val imagePaths = FaceTemplateStore.saveTemplateJpegs(
                context = app,
                profileId = id,
                jpegs = input.templateJpegs,
            )
            val saved = profile.copy(id = id, templateImagePaths = imagePaths)
            if (imagePaths.isNotEmpty()) {
                faceDao.updateTemplatePaths(id, imagePaths)
            }
            onSaved(saved)
        }
    }

    fun deleteFaceProfile(id: Long) {
        viewModelScope.launch {
            FaceTemplateStore.deleteProfileImages(getApplication(), id)
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
        private const val FACE_DUPLICATE_COOLDOWN_MS = 60_000L
    }
}
