package com.dtr.scanner.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

object ScanSound {
    private val player = Executors.newSingleThreadExecutor()

    fun success() {
        player.execute { tone(880.0, 200) }
    }

    fun error() {
        player.execute {
            tone(240.0, 150)
            Thread.sleep(90)
            tone(180.0, 220)
        }
    }

    private fun tone(frequency: Double, durationMs: Int) {
        val sampleRate = 44100
        val count = sampleRate * durationMs / 1000
        val buffer = ShortArray(count) { index ->
            val fade = when {
                index < 300 -> index / 300.0
                index > count - 500 -> (count - index) / 500.0
                else -> 1.0
            }.coerceIn(0.0, 1.0)
            (sin(2.0 * PI * frequency * index / sampleRate) * 32767.0 * 0.9 * fade).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(buffer.size * 2)
            .build()
        track.write(buffer, 0, buffer.size)
        track.play()
        Thread.sleep(durationMs.toLong() + 40)
        track.stop()
        track.release()
    }
}
