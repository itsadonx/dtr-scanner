package com.dtr.scanner.sync

import android.content.Context

class SyncPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_URL, normalizeUrl(value)).apply()

    var deviceKey: String
        get() = prefs.getString(KEY_DEVICE, DEFAULT_DEVICE_KEY).orEmpty().ifBlank { DEFAULT_DEVICE_KEY }
        set(value) = prefs.edit().putString(KEY_DEVICE, value.trim().ifBlank { DEFAULT_DEVICE_KEY }).apply()

    var activeGuardName: String
        get() = prefs.getString(KEY_GUARD, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_GUARD, value.trim()).apply()

    var activeGuardId: String
        get() = prefs.getString(KEY_GUARD_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_GUARD_ID, value.trim()).apply()

    companion object {
        const val DEFAULT_DEVICE_KEY = "dtr-sync-key"
        private const val PREFS_NAME = "cloud_sync"
        private const val KEY_URL = "server_url"
        private const val KEY_DEVICE = "device_key"
        private const val KEY_GUARD = "active_guard"
        private const val KEY_GUARD_ID = "active_guard_id"

        fun normalizeUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
        }
    }
}
