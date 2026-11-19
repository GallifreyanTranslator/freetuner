package com.freetuner.app.audio

import kotlin.math.abs
import kotlin.math.sqrt

object YinPitchDetector {
    fun detectPitch(
        samples: FloatArray,
        sampleRate: Int,
        noiseFloor: Float = 0.015f,
        minFrequencyHz: Float = 50f,
        maxFrequencyHz: Float = 2000f,
        threshold: Float = 0.15f,
    ): PitchDetectionResult? {
        if (samples.isEmpty() || sampleRate <= 0) return null

        val rms = sqrt(samples.fold(0f) { acc, value -> acc + value * value } / samples.size)
        if (rms < noiseFloor) return null

        val minLag = (sampleRate / maxFrequencyHz).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / minFrequencyHz).toInt().coerceAtMost(samples.size / 2)
        if (maxLag <= minLag) return null

        val differenceFunction = computeDifferenceFunction(samples, maxLag)
        val cumulativeMeanNormalized = computeCumulativeMeanNormalized(differenceFunction)

        var tauMin = -1
        for (tau in minLag..maxLag) {
            if (cumulativeMeanNormalized[tau] < threshold) {
                tauMin = tau
                // Standard YIN step 4: slide down to the bottom of this dip. Refining on the
                // descending slope (as before) gave a level-dependent, jittery frequency.
                while (tauMin + 1 <= maxLag &&
                    cumulativeMeanNormalized[tauMin + 1] < cumulativeMeanNormalized[tauMin]
                ) {
                    tauMin++
                }
                break
            }
        }

        if (tauMin < 0) {
            // Nothing under the threshold: fall back to the global minimum instead of
            // silently reporting the highest searchable frequency.
            var best = minLag
            for (tau in minLag..maxLag) {
                if (cumulativeMeanNormalized[tau] < cumulativeMeanNormalized[best]) best = tau
            }
            tauMin = best
        }

        val tauRefined = parabolicInterpolation(cumulativeMeanNormalized, tauMin)
        val frequencyHz = sampleRate / tauRefined

        if (!frequencyHz.isFinite() || frequencyHz < minFrequencyHz || frequencyHz > maxFrequencyHz) {
            return null
        }

        val confidence = 1f - cumulativeMeanNormalized[tauMin.coerceAtMost(cumulativeMeanNormalized.size - 1)]
        if (confidence < 0.55f) return null

        val vibratoInfo = detectVibrato(samples, tauRefined.toInt(), sampleRate)
        if (vibratoInfo.isVibrato && !vibratoInfo.isStable) {
            return null
        }

        return PitchDetectionResult(
            frequencyHz = frequencyHz,
            amplitude = rms,
            confidence = confidence.coerceIn(0f, 1f),
        )
    }

    private fun computeDifferenceFunction(samples: FloatArray, maxLag: Int): FloatArray {
        val df = FloatArray(maxLag + 1)

        for (tau in 1..maxLag) {
            var sum = 0f
            for (i in 0 until samples.size - tau) {
                val delta = samples[i] - samples[i + tau]
                sum += delta * delta
            }
            df[tau] = sum
        }

        return df
    }

    private fun computeCumulativeMeanNormalized(differenceFunction: FloatArray): FloatArray {
        val cmnd = FloatArray(differenceFunction.size)
        cmnd[0] = 1f

        var runningSum = 0f
        for (tau in 1 until differenceFunction.size) {
            runningSum += differenceFunction[tau]
            if (runningSum > 0) {
                cmnd[tau] = differenceFunction[tau] * tau / runningSum
            } else {
                cmnd[tau] = 1f
            }
        }

        return cmnd
    }

    private fun parabolicInterpolation(cmnd: FloatArray, tau: Int): Float {
        if (tau <= 0 || tau >= cmnd.size - 1) return tau.toFloat()

        val x0 = cmnd[tau - 1]
        val x1 = cmnd[tau]
        val x2 = cmnd[tau + 1]

        val shift = (x2 - x0) / (2f * (2f * x1 - x0 - x2))
        return tau + shift
    }

    private fun detectVibrato(
        samples: FloatArray,
        tau: Int,
        sampleRate: Int,
    ): VibratoInfo {
        val fundamentalFreq = sampleRate / tau.toFloat()

        val vibratoLowerHz = fundamentalFreq * 0.95f
        val vibratoUpperHz = fundamentalFreq * 1.05f

        val vibratoLowerTau = (sampleRate / vibratoUpperHz).toInt()
        val vibratoUpperTau = (sampleRate / vibratoLowerHz).toInt()

        val windowSize = (sampleRate * 0.05f).toInt().coerceAtLeast(256)
        var oscillationCount = 0

        if (samples.size < windowSize * 2) {
            return VibratoInfo(isVibrato = false, isStable = true, frequency = fundamentalFreq)
        }

        val halfWindow = windowSize / 2
        for (i in 0 until samples.size - windowSize step halfWindow) {
            val window = samples.sliceArray(i until (i + windowSize))
            val df = computeDifferenceFunction(window, vibratoUpperTau.coerceAtMost(window.size / 2 - 1))
            val cmnd = computeCumulativeMeanNormalized(df)

            var minIdx = vibratoLowerTau.coerceAtMost(cmnd.size - 1)
            var minVal = cmnd[minIdx]

            for (tau in vibratoLowerTau..vibratoUpperTau.coerceAtMost(cmnd.size - 1)) {
                if (cmnd[tau] < minVal) {
                    minVal = cmnd[tau]
                    minIdx = tau
                }
            }

            if (abs(minIdx - tau) > 2) {
                oscillationCount++
            }
        }

        val isVibrato = oscillationCount > 2
        val isStable = oscillationCount <= 1

        return VibratoInfo(
            isVibrato = isVibrato,
            isStable = isStable,
            frequency = fundamentalFreq,
        )
    }

    data class VibratoInfo(
        val isVibrato: Boolean,
        val isStable: Boolean,
        val frequency: Float,
    )

    fun smoothFrequencyWithVibrato(
        previousHz: Float?,
        newHz: Float,
        vibratoDetected: Boolean,
        factor: Float = 0.3f,
    ): Float {
        if (previousHz == null || previousHz <= 0f) return newHz

        // A jump of more than ~a semitone is a new note, not drift: follow it immediately.
        if (abs(newHz - previousHz) / previousHz > 0.06f) return newHz

        val adaptiveFactor = if (vibratoDetected) {
            factor * 0.5f
        } else {
            factor
        }

        val limitedFactor = adaptiveFactor.coerceIn(0.05f, 0.75f)
        return previousHz + (newHz - previousHz) * limitedFactor
    }
}
