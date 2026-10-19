package com.dtr.scanner.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import java.util.Locale

class AttendanceSpeechAnnouncer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = SpeechPreferences(appContext)
    private var tts: TextToSpeech? = null
    @Volatile
    private var ready = false
    @Volatile
    private var pendingMessage: String? = null

    fun initialize() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault()
                pendingMessage?.let { message ->
                    pendingMessage = null
                    speakNow(message)
                }
            }
        }
    }

    fun announceFaceCheckIn(displayName: String) {
        if (!preferences.enabled) return
        speak(preferences.buildAnnouncement(displayName))
    }

    fun previewSample(sampleName: String = "Adonnis Pama") {
        speak(preferences.buildAnnouncement(sampleName))
    }

    private fun speak(message: String) {
        if (tts == null) {
            initialize()
        }
        if (ready) {
            speakNow(message)
        } else {
            pendingMessage = message
        }
    }

    private fun speakNow(message: String) {
        val engine = tts ?: return
        engine.setSpeechRate(preferences.speechRate)
        engine.setPitch(preferences.pitch)
        engine.speak(message, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_FACE_CHECK_IN)
    }

    fun shutdown() {
        pendingMessage = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    fun preferences(): SpeechPreferences = preferences

    companion object {
        private const val UTTERANCE_FACE_CHECK_IN = "face_check_in"
    }
}

@Composable
fun rememberAttendanceSpeechAnnouncer(): AttendanceSpeechAnnouncer {
    val context = androidx.compose.ui.platform.LocalContext.current
    val announcer = remember { AttendanceSpeechAnnouncer(context) }
    DisposableEffect(announcer) {
        announcer.initialize()
        onDispose { announcer.shutdown() }
    }
    return announcer
}
