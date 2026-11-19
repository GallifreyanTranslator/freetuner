package com.freetuner.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class PitchDetectorTest {
    @Test
    fun higherSensitivityLowersTheGate() {
        val lowSensitivityGate = PitchDetector.amplitudeGateForSensitivity(0.25f)
        val highSensitivityGate = PitchDetector.amplitudeGateForSensitivity(0.80f)

        assertTrue(highSensitivityGate < lowSensitivityGate)
    }

    @Test
    fun rejectsSubharmonicLockForConcertA() {
        val sampleRate = 22050
        val samples = FloatArray(sampleRate / 2) { index ->
            sin(2.0 * PI * 440.0 * index / sampleRate).toFloat() * 0.5f
        }

        val result = PitchDetector.detectPitch(samples, sampleRate)

        assertNotNull(result)
        assertEquals("A4", PitchMath.closestNoteName(result!!.frequencyHz))
    }

    @Test
    fun yinDetectsConcertAFromSyntheticSignal() {
        val sampleRate = 22050
        val samples = FloatArray(sampleRate / 2) { index ->
            sin(2.0 * PI * 440.0 * index / sampleRate).toFloat() * 0.5f
        }

        val result = YinPitchDetector.detectPitch(samples, sampleRate)

        assertNotNull(result)
        assertEquals("A4", PitchMath.closestNoteName(result!!.frequencyHz))
    }

    @Test
    fun pyinDetectsConcertAFromSyntheticSignal() {
        val sampleRate = 22050
        val samples = FloatArray(sampleRate / 2) { index ->
            sin(2.0 * PI * 440.0 * index / sampleRate).toFloat() * 0.5f
        }

        val result = PyinPitchDetector.detectPitch(samples, sampleRate)

        assertNotNull(result)
        assertEquals("A4", PitchMath.closestNoteName(result!!.frequencyHz))
    }

    @Test
    fun detectsConcertAFromSyntheticSignal() {
        val sampleRate = 22050
        val samples = FloatArray(sampleRate / 2) { index ->
            sin(2.0 * PI * 440.0 * index / sampleRate).toFloat() * 0.5f
        }

        val result = PitchDetector.detectPitch(samples, sampleRate)

        assertNotNull(result)
        assertTrue(result!!.confidence > 0.55f)
        assertTrue(result.frequencyHz in 430f..450f)
        assertEquals("A4", PitchMath.closestNoteName(result.frequencyHz))
    }
    private fun tone(freq: Double, amp: Float, size: Int = 2048, sampleRate: Int = 44100, phase: Double = 0.0) =
        FloatArray(size) { i ->
            (amp * (sin(2.0 * PI * freq * i / sampleRate + phase) + 0.4 * sin(4.0 * PI * freq * i / sampleRate + phase))).toFloat()
        }

    private fun centsBetween(a: Double, b: Double) = 1200.0 * kotlin.math.ln(a / b) / kotlin.math.ln(2.0)

    @Test
    fun yinIsAccurateAndSteadyOnRealFrameSizes() {
        // Same frame size and rate the app uses (2048 @ 44.1 kHz), strings from low E to high E.
        for (freq in listOf(82.41, 110.0, 196.0, 329.63, 440.0)) {
            val readings = (0 until 8).mapNotNull { n ->
                YinPitchDetector.detectPitch(tone(freq, 0.2f, phase = n * 0.7), 44100, 0.006f)?.frequencyHz?.toDouble()
            }
            assertEquals("no dropouts at $freq Hz", 8, readings.size)
            readings.forEach { assertTrue("$freq Hz off by ${centsBetween(it, freq)} cents", kotlin.math.abs(centsBetween(it, freq)) < 3.0) }
            assertTrue("spread at $freq Hz", centsBetween(readings.max(), readings.min()) < 2.0)
        }
    }

    @Test
    fun yinResultDoesNotDependOnLevel() {
        val loud = YinPitchDetector.detectPitch(tone(220.0, 0.3f), 44100, 0.006f)!!.frequencyHz.toDouble()
        val quiet = YinPitchDetector.detectPitch(tone(220.0, 0.03f), 44100, 0.006f)!!.frequencyHz.toDouble()
        assertTrue(kotlin.math.abs(centsBetween(loud, quiet)) < 1.5)
    }

    @Test
    fun smoothingFollowsNoteChangesImmediately() {
        assertEquals(440f, YinPitchDetector.smoothFrequencyWithVibrato(220f, 440f, false), 0.001f)
        val small = YinPitchDetector.smoothFrequencyWithVibrato(440f, 441f, false)
        assertTrue(small > 440f && small < 441f)
    }
}