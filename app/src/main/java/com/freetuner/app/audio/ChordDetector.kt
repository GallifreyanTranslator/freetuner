package com.freetuner.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChordDetectionResult(
    val chordName: String,
    val confidence: Float,
    val chroma: FloatArray,
)

/**
 * Chord templates as pitch-class profiles (Fujishima-style harmonic pitch class matching):
 * each chord is a binary vector over the 12 pitch classes, correlated against the observed
 * chroma vector from the live FFT.
 */
object ChordTemplates {
    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private val qualities: List<Pair<String, List<Int>>> = listOf(
        "" to listOf(0, 4, 7),
        "m" to listOf(0, 3, 7),
        "dim" to listOf(0, 3, 6),
        "aug" to listOf(0, 4, 8),
        "sus2" to listOf(0, 2, 7),
        "sus4" to listOf(0, 5, 7),
        "7" to listOf(0, 4, 7, 10),
        "maj7" to listOf(0, 4, 7, 11),
        "m7" to listOf(0, 3, 7, 10),
    )

    data class Template(val label: String, val vector: FloatArray, val norm: Float)

    val all: List<Template> = buildList {
        for (root in 0 until 12) {
            for ((qualityName, intervals) in qualities) {
                val vector = FloatArray(12)
                intervals.forEach { interval -> vector[(root + interval) % 12] = 1f }
                add(Template("${noteNames[root]}$qualityName", vector, sqrt(intervals.size.toFloat())))
            }
        }
    }
}

class ChordDetector(
    private val sampleRate: Int = 44100,
) {
    private var recorder: AudioRecord? = null
    private var job: Job? = null

    private val fftSize = 16384
    private val captureSize = 4096
    private val minChromaBin = (60.0 * fftSize / sampleRate).roundToInt().coerceAtLeast(1)
    private val maxChromaBin = (2000.0 * fftSize / sampleRate).roundToInt().coerceAtMost(fftSize / 2 - 1)

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onResult: (ChordDetectionResult?) -> Unit,
    ) {
        if (job != null) return

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(captureSize * 2)

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
            onResult(null)
            return
        }

        recorder = audioRecord
        audioRecord.startRecording()

        job = scope.launch(Dispatchers.Default) {
            val buffer = ShortArray(captureSize)
            val re = FloatArray(fftSize)
            val im = FloatArray(fftSize)

            while (isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read <= 0) continue

                var sumSquares = 0.0
                for (i in 0 until read) {
                    val sample = buffer[i] / Short.MAX_VALUE.toDouble()
                    sumSquares += sample * sample
                }
                val rms = sqrt(sumSquares / read).toFloat()
                if (rms < 0.02f) {
                    withContext(Dispatchers.Main) { onResult(null) }
                    continue
                }

                re.fill(0f)
                im.fill(0f)
                for (i in 0 until read) {
                    val window = 0.5f * (1f - cos(2 * PI * i / (read - 1)).toFloat())
                    re[i] = (buffer[i] / Short.MAX_VALUE.toFloat()) * window
                }
                fft(re, im)

                val chroma = FloatArray(12)
                for (bin in minChromaBin..maxChromaBin) {
                    val magnitude = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
                    if (magnitude <= 0f) continue
                    val frequency = bin * sampleRate.toDouble() / fftSize
                    val midi = 69 + 12 * log2(frequency / 440.0)
                    val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                    chroma[pitchClass] += magnitude
                }

                val chromaNorm = sqrt(chroma.sumOf { (it * it).toDouble() }).toFloat()
                if (chromaNorm < 1e-6f) {
                    withContext(Dispatchers.Main) { onResult(null) }
                    continue
                }
                val normalizedChroma = FloatArray(12) { chroma[it] / chromaNorm }

                var bestLabel = "--"
                var bestScore = -1f
                for (template in ChordTemplates.all) {
                    var dot = 0f
                    for (i in 0 until 12) dot += normalizedChroma[i] * template.vector[i]
                    val score = dot / template.norm
                    if (score > bestScore) {
                        bestScore = score
                        bestLabel = template.label
                    }
                }

                withContext(Dispatchers.Main) {
                    onResult(ChordDetectionResult(bestLabel, bestScore.coerceIn(0f, 1f), normalizedChroma))
                }
            }
        }
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

    /** In-place iterative radix-2 Cooley-Tukey FFT; re.size must be a power of two. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tempRe = re[i]; re[i] = re[j]; re[j] = tempRe
                val tempIm = im[i]; im[i] = im[j]; im[j] = tempIm
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wReal = cos(angle).toFloat()
            val wImag = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curReal = 1f
                var curImag = 0f
                for (k in 0 until len / 2) {
                    val evenIndex = i + k
                    val oddIndex = i + k + len / 2
                    val oddRe = re[oddIndex] * curReal - im[oddIndex] * curImag
                    val oddIm = re[oddIndex] * curImag + im[oddIndex] * curReal
                    re[oddIndex] = re[evenIndex] - oddRe
                    im[oddIndex] = im[evenIndex] - oddIm
                    re[evenIndex] += oddRe
                    im[evenIndex] += oddIm
                    val nextReal = curReal * wReal - curImag * wImag
                    val nextImag = curReal * wImag + curImag * wReal
                    curReal = nextReal
                    curImag = nextImag
                }
                i += len
            }
            len = len shl 1
        }
    }
}
