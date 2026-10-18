package com.dtr.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dtr.scanner.data.AppDatabase
import com.dtr.scanner.data.ScanRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).scanDao()

    val records = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveScan(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            dao.insert(
                ScanRecord(
                    content = trimmed,
                    scannedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }
}
