package com.freetuner.app

import com.freetuner.app.audio.PitchMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuningsTest {
    @Test
    fun parsesNotesWithSharpsAndFlats() {
        assertEquals(listOf("D2", "A2", "D3", "G3", "B3", "E4"), Tunings.parseNotes("D2 A2 D3 G3 B3 E4"))
        assertEquals(listOf("D#2", "A#2"), Tunings.parseNotes("eb2, Bb2"))
    }

    @Test
    fun rejectsInvalidNotes() {
        assertNull(Tunings.parseNotes(""))
        assertNull(Tunings.parseNotes("H2 A2"))
        assertNull(Tunings.parseNotes("E"))
        assertNull(Tunings.parseNotes("E9"))
        assertNull(Tunings.parseNotes((1..13).joinToString(" ") { "E2" }))
    }

    @Test
    fun customTuningsSurviveEncodeDecode() {
        val original = listOf(
            Tunings.custom("Open C", listOf("C2", "G2", "C3", "G3", "C4", "E4")),
            Tunings.custom("Weird|name;here", listOf("D2", "A2")),
        )
        val decoded = Tunings.decode(Tunings.encode(original))
        assertEquals(2, decoded.size)
        assertEquals("Open C", decoded[0].tuningLabel)
        assertEquals(original[0].strings, decoded[0].strings)
        assertEquals("Weirdnamehere", decoded[1].tuningLabel)
    }

    @Test
    fun decodeToleratesGarbage() {
        assertTrue(Tunings.decode("").isEmpty())
        assertTrue(Tunings.decode("nonsense;;|;a|b").isEmpty())
    }

    @Test
    fun everyProPresetHasValidAscendingFrequencies() {
        for (preset in Tunings.pro) {
            assertTrue("${preset.tuningLabel} notes valid", Tunings.parseNotes(preset.strings.joinToString(" ")) != null)
            val freqs = preset.strings.map { PitchMath.frequencyForNoteName(it) }
            assertTrue("${preset.name} ${preset.tuningLabel} frequencies positive", freqs.all { it > 20f })
        }
        assertEquals(Tunings.pro.size, Tunings.pro.map { it.name + it.tuningLabel }.toSet().size)
    }

    @Test
    fun freePresetsAreNotDuplicatedInPro() {
        val free = freePresets.map { it.name + it.tuningLabel }.toSet()
        assertTrue(Tunings.pro.none { (it.name + it.tuningLabel) in free })
    }
}