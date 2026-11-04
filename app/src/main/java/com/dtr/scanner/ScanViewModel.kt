package com.dtr.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dtr.scanner.data.AppDatabase
import com.dtr.scanner.data.FaceEnrollmentInput
import com.dtr.scanner.data.FaceProfile
import com.dtr.scanner.data.FaceTemplateStore
import com.dtr.scanner.data.AttendanceDirection
import com.dtr.scanner.data.ScanRecord
import com.dtr.scanner.data.ScanType
import com.dtr.scanner.face.FaceCheckInPolicy
import com.dtr.scanner.face.FaceSignature
import com.dtr.scanner.qr.QrAttendancePolicy
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dtr.scanner.data.FaceDataBackup

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val scanDao = database.scanDao()
    private val faceDao = database.faceProfileDao()

    val records = scanDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val faceProfiles = faceDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val recentFaceCheckIns = mutableMapOf<String, Long>()
    private val recentQrScans = mutableMapOf<String, Long>()
    private val recentQrDirections = mutableMapOf<String, String>()

    init {
        viewModelScope.launch {
            records.collect {
                syncRecentFaceCheckInsFromHistory(it)
                syncRecentQrScansFromHistory(it)
            }
        }
    }

    fun saveScan(
        content: String,
        scanType: String = ScanType.QR,
        attendanceDirection: String = AttendanceDirection.IN,
    ) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            scanDao.insert(
                ScanRecord(
                    content = trimmed,
                    scannedAtEpochMs = System.currentTimeMillis(),
                    scanType = scanType,
                    attendanceDirection = attendanceDirection,
                ),
            )
        }
    }

    data class QrScanResult(
        val saved: Boolean,
        val duplicate: Boolean,
        val direction: String,
        val content: String,
    )

    fun saveQrScan(content: String, history: List<ScanRecord> = emptyList()): QrScanResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return QrScanResult(
                saved = false,
                duplicate = false,
                direction = AttendanceDirection.IN,
                content = trimmed,
            )
        }
        if (isQrScanDuplicate(trimmed, history)) {
            val lastDirection = recentQrDirections[trimmed]
                ?: history
                    .filter { it.scanType == ScanType.QR && it.content.trim() == trimmed }
                    .maxByOrNull { it.scannedAtEpochMs }
                    ?.attendanceDirection
                ?: AttendanceDirection.IN
            return QrScanResult(
                saved = false,
                duplicate = true,
                direction = lastDirection,
                content = trimmed,
            )
        }
        val direction = nextQrDirection(trimmed, history)
        val now = System.currentTimeMillis()
        recentQrScans[trimmed] = now
        recentQrDirections[trimmed] = direction
        pruneExpiredQrScans()
        saveScan(trimmed, ScanType.QR, direction)
        return QrScanResult(
            saved = true,
            duplicate = false,
            direction = direction,
            content = trimmed,
        )
    }

    fun isFaceCheckInDuplicate(displayName: String, history: List<ScanRecord> = emptyList()): Boolean {
        if (isFaceCheckInDuplicateInMemory(displayName)) return true
        return FaceCheckInPolicy.isDuplicateInRecords(history, displayName)
    }

    /** Returns true when a new face attendance row was saved. */
    fun saveFaceRecognition(displayName: String, history: List<ScanRecord> = emptyList()): Boolean {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return false
        if (isFaceCheckInDuplicate(trimmed, history)) {
            return false
        }
        val now = System.currentTimeMillis()
        recentFaceCheckIns[trimmed] = now
        pruneExpiredFaceCheckIns()
        saveScan(
            FaceCheckInPolicy.recordContent(trimmed),
            ScanType.FACE,
            AttendanceDirection.IN,
        )
        return true
    }

    private fun nextQrDirection(qrContent: String, history: List<ScanRecord>): String {
        val fromHistory = QrAttendancePolicy.nextDirection(history, qrContent)
        val lastMs = recentQrScans[qrContent] ?: return fromHistory
        val lastDirection = recentQrDirections[qrContent] ?: return fromHistory
        val lastHistoryMs = history
            .filter { it.scanType == ScanType.QR && it.content.trim() == qrContent }
            .maxOfOrNull { it.scannedAtEpochMs } ?: 0L
        if (lastMs <= lastHistoryMs) return fromHistory
        return if (AttendanceDirection.isOut(lastDirection)) {
            AttendanceDirection.IN
        } else {
            AttendanceDirection.OUT
        }
    }

    private fun isQrScanDuplicate(qrContent: String, history: List<ScanRecord>): Boolean {
        pruneExpiredQrScans()
        val lastMs = recentQrScans[qrContent]
        if (lastMs != null && System.currentTimeMillis() - lastMs < QrAttendancePolicy.DUPLICATE_WINDOW_MS) {
            return true
        }
        return QrAttendancePolicy.isDuplicate(history, qrContent)
    }

    private fun syncRecentQrScansFromHistory(history: List<ScanRecord>) {
        val now = System.currentTimeMillis()
        val since = now - QrAttendancePolicy.DUPLICATE_WINDOW_MS
        history.filter { it.scanType == ScanType.QR && it.scannedAtEpochMs >= since }
            .forEach { record ->
                val key = record.content.trim()
                if (key.isEmpty()) return@forEach
                val existing = recentQrScans[key]
                if (existing == null || record.scannedAtEpochMs > existing) {
                    recentQrScans[key] = record.scannedAtEpochMs
                    recentQrDirections[key] = record.attendanceDirection
                }
            }
        pruneExpiredQrScans()
    }

    private fun pruneExpiredQrScans() {
        val now = System.currentTimeMillis()
        val expired = recentQrScans.filterValues {
            now - it >= QrAttendancePolicy.DUPLICATE_WINDOW_MS
        }.keys
        expired.forEach {
            recentQrScans.remove(it)
            recentQrDirections.remove(it)
        }
    }

    private fun isFaceCheckInDuplicateInMemory(displayName: String): Boolean {
        val trimmed = displayName.trim()
        if (trimmed.isEmpty()) return false
        pruneExpiredFaceCheckIns()
        val lastMs = recentFaceCheckIns[trimmed] ?: return false
        return System.currentTimeMillis() - lastMs < FaceCheckInPolicy.DUPLICATE_WINDOW_MS
    }

    private fun syncRecentFaceCheckInsFromHistory(history: List<ScanRecord>) {
        val now = System.currentTimeMillis()
        val since = now - FaceCheckInPolicy.DUPLICATE_WINDOW_MS
        history.filter { it.scanType == ScanType.FACE && it.scannedAtEpochMs >= since }
            .forEach { record ->
                val name = record.content.removePrefix("Face check-in: ").trim()
                if (name.isEmpty()) return@forEach
                val existing = recentFaceCheckIns[name]
                if (existing == null || record.scannedAtEpochMs > existing) {
                    recentFaceCheckIns[name] = record.scannedAtEpochMs
                }
            }
        pruneExpiredFaceCheckIns()
    }

    private fun pruneExpiredFaceCheckIns() {
        val now = System.currentTimeMillis()
        val expired = recentFaceCheckIns.filterValues {
            now - it >= FaceCheckInPolicy.DUPLICATE_WINDOW_MS
        }.keys
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

    fun exportBackup(uri: Uri, onComplete: (Result<FaceDataBackup.ImportSummary>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val profiles = faceDao.getAllOnce()
                    val scans = scanDao.getAllOnce()
                    app.contentResolver.openOutputStream(uri)?.use { output ->
                        FaceDataBackup.export(app, output, profiles, scans)
                    } ?: error("Could not open export destination.")
                    FaceDataBackup.ImportSummary(
                        faceProfilesImported = profiles.size,
                        scanRecordsImported = scans.size,
                    )
                }
            }
            onComplete(result)
        }
    }

    fun importBackup(uri: Uri, onComplete: (Result<FaceDataBackup.ImportSummary>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        FaceDataBackup.import(app, input)
                    } ?: error("Could not read backup file.")
                }
            }
            onComplete(result)
        }
    }

}
