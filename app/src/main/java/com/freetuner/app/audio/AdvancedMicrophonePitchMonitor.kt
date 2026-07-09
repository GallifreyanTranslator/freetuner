package com.freetuner.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdvancedMicrophonePitchMonitor(
    private val sampleRate: Int = 44100,
    private val algorithm: PitchDetectionAlgorithm = PitchDetectionAlgorithm.PHASE2_YIN,
) {
    private var recorder: AudioRecord? = null
    private var job: Job? = null
    private var noiseFloor = 0.006f
    private var silentFrameCount = 0
    private var previousFrequency: Float? = null

    enum class PitchDetectionAlgorithm {
        PHASE1_OPTIMIZED,
        PHASE2_YIN,
        PHASE3_PYIN,
    }

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onPitchDetected: (PitchDetectionResult?) -> Unit,
    ) {
        if (job != null) return

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(8192)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer,
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            recorder = null
            onPitchDetected(null)
            return
        }

        recorder = audioRecord
        audioRecord.startRecording()

        job = scope.launch(Dispatchers.Default) {
            val buffer = ShortArray(4096)
            while (isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                val samples = FloatArray(read) { i ->
                    (buffer[i] / Short.MAX_VALUE.toFloat()) * hannWindow(read, i)
                }

                val rms = sqrt(samples.fold(0f) { acc, value -> acc + value * value } / samples.size)
                if (rms < noiseFloor * 1.15f) {
                    silentFrameCount++
                    if (silentFrameCount > 3) {
                        noiseFloor = (noiseFloor * 0.85f + rms * 0.15f).coerceAtLeast(0.002f)
                    }
                } else {
                    silentFrameCount = 0
                    noiseFloor = (noiseFloor * 0.95f + rms * 0.05f).coerceAtLeast(0.002f)
                }

                val result = when (algorithm) {
                    PitchDetectionAlgorithm.PHASE1_OPTIMIZED ->
                        PitchDetector.detectPitch(samples, sampleRate, noiseFloor)
                    PitchDetectionAlgorithm.PHASE2_YIN ->
                        YinPitchDetector.detectPitch(samples, sampleRate, noiseFloor)
                    PitchDetectionAlgorithm.PHASE3_PYIN ->
                        PyinPitchDetector.detectPitch(samples, sampleRate, noiseFloor)
                }

                val smoothedResult = result?.let { res ->
                    val smoothedFreq = YinPitchDetector.smoothFrequencyWithVibrato(
                        previousFrequency,
                        res.frequencyHz,
                        false,
                    )
                    previousFrequency = smoothedFreq
                    res.copy(frequencyHz = smoothedFreq)
                }

                withContext(Dispatchers.Main) {
                    onPitchDetected(smoothedResult)
                }
            }
        }
    }

    private fun hannWindow(size: Int, index: Int): Float {
        return ((1 - cos(2 * PI * index / (size - 1))) / 2).toFloat()
    }

    suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        recorder?.apply {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        recorder = null
    }

    fun switchAlgorithm(newAlgorithm: PitchDetectionAlgorithm) {
        previousFrequency = null
    }
}
