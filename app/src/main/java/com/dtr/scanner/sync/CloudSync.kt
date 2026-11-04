package com.dtr.scanner.sync

import com.dtr.scanner.data.AppDatabase
import com.dtr.scanner.data.GuardAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class CloudSync(
    private val database: AppDatabase,
    private val preferences: SyncPreferences,
) {
    private val mutex = Mutex()

    suspend fun syncAll(): String = mutex.withLock {
        val base = preferences.serverUrl
        if (base.isBlank()) {
            return@withLock "Add the companion website address to sync."
        }
        val guards = pullGuards(base)
        val uploaded = pushScans(base)
        "Synced $guards guard account${if (guards == 1) "" else "s"}, uploaded $uploaded scan${if (uploaded == 1) "" else "s"}."
    }

    private suspend fun pullGuards(base: String): Int = withContext(Dispatchers.IO) {
        val response = request("GET", "$base/api/sync/guards", null)
        val array = response.optJSONArray("guards") ?: JSONArray()
        val accounts = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = item.optString("id")
                val guardId = item.optString("guardId")
                if (id.isBlank() || guardId.isBlank()) continue
                add(
                    GuardAccount(
                        id = id,
                        guardId = guardId,
                        displayName = item.optString("displayName").ifBlank { guardId },
                        passwordSalt = item.optString("passwordSalt"),
                        passwordHash = item.optString("passwordHash"),
                        active = item.optBoolean("active", true),
                        updatedAtEpochMs = item.optLong("updatedAtEpochMs"),
                    ),
                )
            }
        }
        val dao = database.guardAccountDao()
        dao.deleteAll()
        if (accounts.isNotEmpty()) dao.insertAll(accounts)
        accounts.size
    }

    private suspend fun pushScans(base: String): Int = withContext(Dispatchers.IO) {
        val dao = database.scanDao()
        val pending = dao.unsynced()
        if (pending.isEmpty()) return@withContext 0
        val ready = pending.map { record ->
            if (record.clientId.isNotBlank()) {
                record
            } else {
                val clientId = UUID.randomUUID().toString()
                dao.setClientId(record.id, clientId)
                record.copy(clientId = clientId)
            }
        }
        val payload = JSONObject().put(
            "scans",
            JSONArray().apply {
                ready.forEach { record ->
                    put(
                        JSONObject()
                            .put("clientId", record.clientId)
                            .put("content", record.content)
                            .put("scannedAtEpochMs", record.scannedAtEpochMs)
                            .put("scanType", record.scanType)
                            .put("attendanceDirection", record.attendanceDirection)
                            .put("recordedBy", record.recordedBy),
                    )
                }
            },
        )
        val response = request("POST", "$base/api/sync/scans", payload)
        val saved = response.optJSONArray("savedClientIds") ?: JSONArray()
        val ids = buildList {
            for (index in 0 until saved.length()) {
                val id = saved.optString(index)
                if (id.isNotBlank()) add(id)
            }
        }
        if (ids.isNotEmpty()) dao.markSynced(ids)
        ids.size
    }

    private fun request(method: String, url: String, body: JSONObject?): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Device-Key", preferences.deviceKey)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { stream ->
                    stream.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                error(message.ifBlank { "Website returned HTTP $code." })
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (error: IllegalStateException) {
            throw error
        } catch (_: Exception) {
            throw IllegalStateException(
                "Cannot reach the companion website. Check the address and internet connection.",
            )
        } finally {
            connection.disconnect()
        }
    }
}
