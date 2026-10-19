package com.dtr.scanner.speech

import android.content.Context
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AttendancePhrase(val spoken: String) {
    TIME_IN("Time In"),
    TIME_OUT("Time Out"),
    CHECKED_IN("Checked In"),
    ;

    companion object {
        fun fromStored(value: String?): AttendancePhrase {
            return entries.firstOrNull { it.name == value } ?: TIME_IN
        }
    }
}

class SpeechPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var speechRate: Float
        get() = prefs.getFloat(KEY_RATE, DEFAULT_RATE)
        set(value) = prefs.edit().putFloat(KEY_RATE, value).apply()

    var pitch: Float
        get() = prefs.getFloat(KEY_PITCH, DEFAULT_PITCH)
        set(value) = prefs.edit().putFloat(KEY_PITCH, value).apply()

    var attendancePhrase: AttendancePhrase
        get() = AttendancePhrase.fromStored(prefs.getString(KEY_PHRASE, AttendancePhrase.TIME_IN.name))
        set(value) = prefs.edit().putString(KEY_PHRASE, value.name).apply()

    var sayThankYou: Boolean
        get() = prefs.getBoolean(KEY_THANK_YOU, true)
        set(value) = prefs.edit().putBoolean(KEY_THANK_YOU, value).apply()

    var use24HourTime: Boolean
        get() = prefs.getBoolean(KEY_TIME_24H, false)
        set(value) = prefs.edit().putBoolean(KEY_TIME_24H, value).apply()

    var preferMaleVoice: Boolean
        get() = prefs.getBoolean(KEY_MALE_VOICE, true)
        set(value) = prefs.edit().putBoolean(KEY_MALE_VOICE, value).apply()

    var thankYouPauseMs: Long
        get() = prefs.getLong(KEY_THANK_PAUSE_MS, DEFAULT_THANK_PAUSE_MS)
        set(value) = prefs.edit().putLong(KEY_THANK_PAUSE_MS, value).apply()

    fun buildMainAnnouncement(displayName: String): String {
        val trimmed = displayName.trim()
        val time = formatSpeechTime()
        val parts = buildList {
            if (trimmed.isNotEmpty()) add(trimmed)
            add(attendancePhrase.spoken)
            add(time)
        }
        return parts.joinToString(" ")
    }

    fun thankYouMessage(): String = "Thank you!"

    /** Preview text shown in settings (pause shown as ellipsis). */
    fun buildAnnouncement(displayName: String): String {
        val main = buildMainAnnouncement(displayName)
        return if (sayThankYou) {
            "$main … ${thankYouMessage()}"
        } else {
            main
        }
    }

    private fun formatSpeechTime(): String {
        return if (use24HourTime) {
            LocalTime.now().format(TIME_24H_FORMATTER)
        } else {
            LocalTime.now()
                .format(TIME_12H_FORMATTER)
                .replace(" ", "")
                .uppercase(Locale.US)
        }
    }

    companion object {
        private const val PREFS_NAME = "attendance_speech"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_RATE = "rate"
        private const val KEY_PITCH = "pitch"
        private const val KEY_PHRASE = "phrase"
        private const val KEY_THANK_YOU = "thank_you"
        private const val KEY_TIME_24H = "time_24h"
        private const val KEY_MALE_VOICE = "male_voice"
        private const val KEY_THANK_PAUSE_MS = "thank_pause_ms"

        const val DEFAULT_RATE = 1f
        const val DEFAULT_PITCH = 0.92f
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 1.5f
        const val MIN_PITCH = 0.7f
        const val MAX_PITCH = 1.4f
        const val DEFAULT_THANK_PAUSE_MS = 900L
        const val MIN_THANK_PAUSE_MS = 300L
        const val MAX_THANK_PAUSE_MS = 2500L

        private val TIME_12H_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        private val TIME_24H_FORMATTER = DateTimeFormatter.ofPattern("H:mm", Locale.US)
    }
}
