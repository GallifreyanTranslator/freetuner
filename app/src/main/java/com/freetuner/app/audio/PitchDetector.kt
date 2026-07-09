package com.freetuner.app.audio

import kotlin.math.abs
import kotlin.math.sqrt

data class PitchDetectionResult(
    val frequencyHz: Float,
    val amplitude: Float,
    val confidence: Float,
)

object PitchDetector {
    fun detectPitch(
        samples: FloatArray,
        sampleRate: Int,
        noiseFloor: Float = 0.015f,
        minFrequencyHz: Float = 50f,
        maxFrequencyHz: Float = 2000f,
    ): PitchDetectionResult? {
        if (samples.isEmpty() || sampleRate <= 0) return null

        val normalized = FloatArray(samples.size)
        var mean = 0f
        for (index in samples.indices) {
            mean += samples[index]
        }
        mean /= samples.size

        var energy = 0f
        for (index in samples.indices) {
            val value = samples[index] - mean
            normalized[index] = value
            energy += value * value
        }

        val rms = sqrt(energy / samples.size)
        if (rms < noiseFloor) return null

        val minLag = (sampleRate / maxFrequencyHz).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / minFrequencyHz).toInt().coerceAtMost(samples.size / 2)
        if (maxLag <= minLag) return null

        var bestLag = -1
        var bestCorrelation = Float.NEGATIVE_INFINITY
        val correlations = FloatArray(maxLag - minLag + 1)

        for (lag in minLag..maxLag) {
            var correlation = 0f
            var normA = 0f
            var normB = 0f

            for (i in 0 until normalized.size - lag) {
                val a = normalized[i]
                val b = normalized[i + lag]
                correlation += a * b
                normA += a * a
                normB += b * b
            }

            if (normA <= 0f || normB <= 0f) continue

            val normalizedCorrelation = correlation / sqrt(normA * normB)
            correlations[lag - minLag] = normalizedCorrelation
            if (normalizedCorrelation > bestCorrelation) {
                bestCorrelation = normalizedCorrelation
                bestLag = lag
            }
        }

        if (bestLag <= 0) return null

        val confidence = bestCorrelation.coerceIn(0f, 1f)
        if (confidence < 0.70f) return null

        val frequencyHz = sampleRate / bestLag.toFloat()
        if (!frequencyHz.isFinite() || frequencyHz < minFrequencyHz || frequencyHz > maxFrequencyHz) {
            return null
        }

        if (!validateOctave(frequencyHz, correlations, minLag, sampleRate)) {
            return null
        }

        return PitchDetectionResult(
            frequencyHz = frequencyHz,
            amplitude = rms,
            confidence = confidence,
        )
    }

    private fun validateOctave(
        frequency: Float,
        correlations: FloatArray,
        minLag: Int,
        sampleRate: Int,
    ): Boolean {
        val octaveFreq = frequency / 2f
        if (octaveFreq < 50f) return true

        val octaveLag = (sampleRate / octaveFreq).toInt()
        val octaveIdx = octaveLag - minLag

        if (octaveIdx < 0 || octaveIdx >= correlations.size) return true

        val fundamentalCorr = correlations.maxOrNull() ?: 0f
        val octaveCorr = correlations.getOrNull(octaveIdx) ?: 0f

        return fundamentalCorr > octaveCorr * 1.3f
    }

    fun smoothFrequency(previousHz: Float?, newHz: Float, factor: Float = 0.18f): Float {
        if (previousHz == null || previousHz <= 0f) return newHz
        val limitedFactor = factor.coerceIn(0.05f, 0.85f)
        val drift = abs(newHz - previousHz) / previousHz.coerceAtLeast(1f)
        val adaptiveFactor = when {
            drift < 0.015f -> 0.10f
            drift < 0.08f -> limitedFactor
            else -> 0.34f
        }
        return previousHz + (newHz - previousHz) * adaptiveFactor
    }

    fun amplitudeGateForSensitivity(sensitivity: Float): Float {
        val normalized = sensitivity.coerceIn(0f, 1f)
        return 0.002f + (1f - normalized) * 0.028f
    }

    fun passesSensitivityGate(result: PitchDetectionResult?, sensitivity: Float): Boolean {
        return result != null && result.amplitude >= amplitudeGateForSensitivity(sensitivity)
    }

    fun isStable(result: PitchDetectionResult?): Boolean {
        return result != null && result.amplitude > 0.02f && result.confidence > 0.72f
    }

    fun noiseLabel(result: PitchDetectionResult?): String {
        return when {
            result == null -> "Listening..."
            result.amplitude < 0.02f -> "Too quiet"
            result.confidence < 0.72f -> "Noisy"
            else -> "Stable"
        }
    }

    fun centsToNeedle(cents: Int): Float {
        return (cents.coerceIn(-50, 50) / 50f).coerceIn(-1f, 1f)
    }

    fun amplitudePercent(result: PitchDetectionResult?): Int {
        if (result == null) return 0
        return (abs(result.amplitude) * 100).toInt().coerceIn(0, 100)
    }
}
