package com.freetuner.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh

class ReferenceTonePlayer {
    @Volatile
    private var isPlaying = false
    private var audioTrack: AudioTrack? = null
    private var playThread: Thread? = null

    fun playTone(frequencyHz: Double) {
        stop()

        val sampleRate = 48000
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val channelCount = 2
        val bufferFrames = 1024
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        val trackBuilder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(max(minBuffer, bufferFrames * channelCount * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        val track = trackBuilder.build().apply {
            setVolume(AudioTrack.getMaxVolume())
        }

        audioTrack = track
        isPlaying = true

        playThread = Thread {
            var phase = 0.0
            var stereoPhase = 0.0
            val phaseStep = 2.0 * PI * frequencyHz / sampleRate
            val stereoStep = 2.0 * PI * 0.65 / sampleRate
            val buffer = ShortArray(bufferFrames * channelCount)
            val attackDuration = (sampleRate / 30).coerceAtLeast(1)
            var attackFrames = attackDuration

            track.play()

            while (isPlaying) {
                for (frame in 0 until bufferFrames) {
                    val fundamental = sin(phase)
                    val octave = sin(phase * 2.0) * 0.34
                    val fifth = sin(phase * 3.0) * 0.14
                    val shimmer = sin(phase * 4.0 + stereoPhase) * 0.08
                    val envelope = if (attackFrames > 0) {
                        1.0 - (attackFrames.toDouble() / attackDuration)
                    } else {
                        1.0
                    }

                    val speakerFriendly = tanh((fundamental * 0.74 + octave + fifth + shimmer) * 1.55) * envelope * 0.86
                    val left = (speakerFriendly * (0.97 + sin(stereoPhase) * 0.03)).coerceIn(-1.0, 1.0)
                    val right = (speakerFriendly * (0.97 - sin(stereoPhase) * 0.03)).coerceIn(-1.0, 1.0)

                    buffer[frame * 2] = (left * Short.MAX_VALUE).toInt().toShort()
                    buffer[frame * 2 + 1] = (right * Short.MAX_VALUE).toInt().toShort()

                    phase += phaseStep
                    stereoPhase += stereoStep
                    if (phase > 2.0 * PI) phase -= 2.0 * PI
                    if (stereoPhase > 2.0 * PI) stereoPhase -= 2.0 * PI
                    if (attackFrames > 0) attackFrames--
                }

                val written = track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isPlaying = false
        playThread?.join(80)
        playThread = null

        audioTrack?.apply {
            try {
                pause()
                flush()
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        audioTrack = null
    }
}
