package com.dtr.scanner.speech

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
    private var pendingDisplayName: String? = null

    fun initialize() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                applyVoiceSelection()
                pendingDisplayName?.let { name ->
                    pendingDisplayName = null
                    speakCheckIn(name)
                }
            }
        }
    }

    fun announceFaceCheckIn(displayName: String) {
        if (!preferences.enabled) return
        speak(displayName)
    }

    fun previewSample(sampleName: String = "Adonnis Pama") {
        speak(sampleName)
    }

    private fun speak(displayName: String) {
        if (tts == null) {
            initialize()
        }
        if (ready) {
            speakCheckIn(displayName)
        } else {
            pendingDisplayName = displayName
        }
    }

    private fun speakCheckIn(displayName: String) {
        val engine = tts ?: return
        applyVoiceSelection()
        engine.setSpeechRate(preferences.speechRate)
        engine.setPitch(preferences.pitch)

        val main = preferences.buildMainAnnouncement(displayName)
        engine.speak(main, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_MAIN)

        if (preferences.sayThankYou) {
            val pauseMs = preferences.thankYouPauseMs.coerceIn(
                SpeechPreferences.MIN_THANK_PAUSE_MS,
                SpeechPreferences.MAX_THANK_PAUSE_MS,
            )
            if (pauseMs > 0) {
                @Suppress("DEPRECATION")
                engine.playSilentUtterance(pauseMs, TextToSpeech.QUEUE_ADD, UTTERANCE_PAUSE)
            }
            engine.speak(
                preferences.thankYouMessage(),
                TextToSpeech.QUEUE_ADD,
                null,
                UTTERANCE_THANKS,
            )
        }
    }

    private fun applyVoiceSelection() {
        val engine = tts ?: return
        engine.language = Locale.getDefault()
        if (!preferences.preferMaleVoice || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }
        val locale = Locale.getDefault()
        val voices = engine.voices ?: return
        val maleVoice = voices
            .asSequence()
            .filter { voice ->
                voice.locale.language.equals(locale.language, ignoreCase = true) &&
                    !voice.isNetworkConnectionRequired
            }
            .sortedByDescending { voiceScore(it) }
            .firstOrNull { isLikelyMaleVoice(it) }
        if (maleVoice != null) {
            engine.voice = maleVoice
        } else {
            // Slightly lower pitch helps when no explicit male voice pack is installed.
            engine.setPitch(preferences.pitch.coerceAtMost(0.95f))
        }
    }

    private fun voiceScore(voice: Voice): Int {
        var score = 0
        val name = voice.name.lowercase(Locale.US)
        if (name.contains("local")) score += 2
        if (name.contains("high")) score += 1
        if (isLikelyMaleVoice(voice)) score += 5
        return score
    }

    private fun isLikelyMaleVoice(voice: Voice): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val features = voice.features ?: emptySet()
            if (features.any { it.equals("male", ignoreCase = true) }) return true
            if (features.any { it.equals("female", ignoreCase = true) }) return false
        }
        val name = voice.name.lowercase(Locale.US)
        if (name.contains("female") || name.contains("woman") || name.contains("-f-")) {
            return false
        }
        if (name.contains("male") || name.contains("man") || name.contains("-m-")) {
            return true
        }
        // Common Google offline male voice id fragments on many devices.
        return name.contains("iom") || name.contains("gbb") || name.contains("#male")
    }

    fun shutdown() {
        pendingDisplayName = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    fun preferences(): SpeechPreferences = preferences

    companion object {
        private const val UTTERANCE_MAIN = "face_check_in_main"
        private const val UTTERANCE_PAUSE = "face_check_in_pause"
        private const val UTTERANCE_THANKS = "face_check_in_thanks"
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
