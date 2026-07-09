package com.freetuner.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchMathTest {
    @Test
    fun mapsConcertA() {
        assertEquals("A4", PitchMath.closestNoteName(440f))
        assertEquals(0, PitchMath.centsOffFromNearest(440f))
    }

    @Test
    fun detectsHigherSemitone() {
        assertEquals("A#4", PitchMath.closestNoteName(466.16f))
    }

    @Test
    fun centsGoPositiveWhenSharp() {
        assertTrue(PitchMath.centsOffFromNearest(442f) > 0)
    }
}
