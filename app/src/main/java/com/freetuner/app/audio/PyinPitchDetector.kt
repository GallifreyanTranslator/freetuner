package com.freetuner.app.audio

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

object PyinPitchDetector {
    fun detectPitch(
        samples: FloatArray,
        sampleRate: Int,
        noiseFloor: Float = 0.015f,
        minFrequencyHz: Float = 50f,
        maxFrequencyHz: Float = 2000f,
        threshold: Float = 0.1f,
    ): PitchDetectionResult? {
        if (samples.isEmpty() || sampleRate <= 0) return null

        val rms = sqrt(samples.fold(0f) { acc, value -> acc + value * value } / samples.size)
        if (rms < noiseFloor) return null

        val minLag = (sampleRate / maxFrequencyHz).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / minFrequencyHz).toInt().coerceAtMost(samples.size / 2)
        if (maxLag <= minLag) return null

        val differenceFunction = computeDifferenceFunction(samples, maxLag)
        val cumulativeMeanNormalized = computeCumulativeMeanNormalized(differenceFunction)

        val candidates = findCandidates(cumulativeMeanNormalized, minLag, maxLag, threshold)
        if (candidates.isEmpty()) return null

        val selectedCandidate = selectBestCandidate(candidates, sampleRate, rms)
        if (selectedCandidate == null) return null

        val confidence = 1f - cumulativeMeanNormalized[selectedCandidate.tau.coerceAtMost(cumulativeMeanNormalized.size - 1)]
        val frequencyHz = sampleRate / selectedCandidate.tauRefined

        if (!frequencyHz.isFinite() || frequencyHz < minFrequencyHz || frequencyHz > maxFrequencyHz) {
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

    private fun findCandidates(
        cmnd: FloatArray,
        minLag: Int,
        maxLag: Int,
        threshold: Float,
    ): List<Candidate> {
        val candidates = mutableListOf<Candidate>()

        for (tau in minLag..maxLag) {
            if (tau >= cmnd.size) break
            if (cmnd[tau] < threshold) {
                val tauRefined = parabolicInterpolation(cmnd, tau)
                candidates.add(
                    Candidate(
                        tau = tau,
                        tauRefined = tauRefined,
                        salience = 1f - cmnd[tau],
                    )
                )
            }
        }

        return candidates
    }

    private fun selectBestCandidate(
        candidates: List<Candidate>,
        sampleRate: Int,
        amplitude: Float,
    ): Candidate? {
        if (candidates.isEmpty()) return null

        var bestCandidate = candidates[0]
        var bestScore = scoreCandidateByAmplitude(candidates[0], sampleRate, amplitude)

        for (candidate in candidates.drop(1)) {
            val score = scoreCandidateByAmplitude(candidate, sampleRate, amplitude)
            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }

        return bestCandidate
    }

    private fun scoreCandidateByAmplitude(
        candidate: Candidate,
        sampleRate: Int,
        amplitude: Float,
    ): Float {
        val frequency = sampleRate / candidate.tauRefined

        val frequencyLogScore = 1f / (1f + abs(log2(frequency / 440f)))
        val salienceScore = candidate.salience
        val amplitudeScore = amplitude.coerceIn(0f, 1f)

        return (frequencyLogScore * 0.3f + salienceScore * 0.5f + amplitudeScore * 0.2f)
    }

    private fun parabolicInterpolation(cmnd: FloatArray, tau: Int): Float {
        if (tau <= 0 || tau >= cmnd.size - 1) return tau.toFloat()

        val x0 = cmnd[tau - 1]
        val x1 = cmnd[tau]
        val x2 = cmnd[tau + 1]

        val denominator = 2f * (2f * x1 - x0 - x2)
        if (denominator == 0f) return tau.toFloat()

        val shift = (x2 - x0) / denominator
        return tau + shift.coerceIn(-0.5f, 0.5f)
    }

    private data class Candidate(
        val tau: Int,
        val tauRefined: Float,
        val salience: Float,
    )

    fun analyzePolyphony(
        samples: FloatArray,
        sampleRate: Int,
    ): PolyphonyInfo {
        val minLag = (sampleRate / 2000f).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / 50f).toInt().coerceAtMost(samples.size / 2)

        val differenceFunction = computeDifferenceFunction(samples, maxLag)
        val cumulativeMeanNormalized = computeCumulativeMeanNormalized(differenceFunction)

        val candidates = findCandidates(cumulativeMeanNormalized, minLag, maxLag, 0.15f)

        val peakCount = candidates.size
        val isMonophonic = peakCount <= 1
        val isPolyphonic = peakCount >= 2

        return PolyphonyInfo(
            peakCount = peakCount,
            isMonophonic = isMonophonic,
            isPolyphonic = isPolyphonic,
            confidence = if (isMonophonic) 0.9f else 0.6f,
        )
    }

    data class PolyphonyInfo(
        val peakCount: Int,
        val isMonophonic: Boolean,
        val isPolyphonic: Boolean,
        val confidence: Float,
    )

    fun detectHarmonics(
        samples: FloatArray,
        fundamentalHz: Float,
        sampleRate: Int,
    ): HarmonicAnalysis {
        val harmonics = mutableListOf<HarmonicInfo>()

        for (harmonic in 2..4) {
            val harmonicHz = fundamentalHz * harmonic
            val harmonicTau = sampleRate / harmonicHz
            val searchRange = (harmonicTau * 0.1f).toInt().coerceAtLeast(1)

            var maxEnergy = 0f
            var peakTau = harmonicTau.toInt()

            for (tau in (harmonicTau - searchRange).toInt()..(harmonicTau + searchRange).toInt()) {
                if (tau <= 0 || tau >= samples.size / 2) continue

                var energy = 0f
                for (i in 0 until samples.size - tau) {
                    energy += samples[i] * samples[i + tau]
                }

                if (energy > maxEnergy) {
                    maxEnergy = energy
                    peakTau = tau
                }
            }

            val detectedHz = sampleRate / peakTau.toFloat()
            val frequency = detectedHz.takeIf { it > 0 } ?: 0f
            val strength = (maxEnergy / (samples.size * 100f)).coerceIn(0f, 1f)

            harmonics.add(
                HarmonicInfo(
                    harmonic = harmonic,
                    expectedHz = harmonicHz,
                    detectedHz = frequency,
                    strength = strength,
                    present = strength > 0.2f,
                )
            )
        }

        val fundamentalStrength = harmonics.getOrNull(0)?.strength ?: 0f
        val harmonicStrength = harmonics.drop(1).maxOfOrNull { it.strength } ?: 0f

        return HarmonicAnalysis(
            harmonics = harmonics,
            fundamentalDominant = fundamentalStrength > harmonicStrength * 1.5f,
        )
    }

    data class HarmonicInfo(
        val harmonic: Int,
        val expectedHz: Float,
        val detectedHz: Float,
        val strength: Float,
        val present: Boolean,
    )

    data class HarmonicAnalysis(
        val harmonics: List<HarmonicInfo>,
        val fundamentalDominant: Boolean,
    )
}
