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
    fun detectsConcertAFromSyntheticSignal() {
        val sampleRate = 22050
        val samples = ShortArray(sampleRate / 2) { index ->
            val sample = sin(2.0 * PI * 440.0 * index / sampleRate)
            (sample * Short.MAX_VALUE * 0.5).toInt().toShort()
        }

        val result = PitchDetector.detectPitch(samples, sampleRate)

        assertNotNull(result)
        assertTrue(result!!.confidence > 0.55f)
        assertTrue(result.frequencyHz in 430f..450f)
        assertEquals("A4", PitchMath.closestNoteName(result.frequencyHz))
    }
}
