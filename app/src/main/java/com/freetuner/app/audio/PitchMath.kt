package com.freetuner.app.audio

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

object PitchMath {
    private val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun closestMidiFromFrequency(frequencyHz: Float): Int {
        if (frequencyHz <= 0f) return 69
        return (69 + 12 * log2(frequencyHz / 440f)).roundToInt()
    }

    fun closestNoteName(frequencyHz: Float): String {
        if (frequencyHz <= 0f) return "--"
        val midi = closestMidiFromFrequency(frequencyHz)
        return noteNameForMidi(midi)
    }

    fun noteNameForMidi(midi: Int): String {
        val note = noteNames[(midi % 12 + 12) % 12]
        val octave = midi / 12 - 1
        return "$note$octave"
    }

    fun referenceForMidi(midi: Int, referenceA4Hz: Float = 440f): Float {
        return referenceA4Hz * 2f.pow((midi - 69) / 12f)
    }

    fun centsOffFromNearest(frequencyHz: Float, referenceA4Hz: Float = 440f): Int {
        if (frequencyHz <= 0f) return 0
        val midi = closestMidiFromFrequency(frequencyHz)
        val targetHz = referenceForMidi(midi, referenceA4Hz)
        return (1200 * log2(frequencyHz / targetHz)).roundToInt()
    }

    fun frequencyForNoteName(noteName: String, referenceA4Hz: Float = 440f): Float {
        val normalized = noteName.trim().uppercase()
        if (normalized.isBlank()) return referenceA4Hz

        val notePart = normalized.takeWhile { it.isLetter() || it == '#' }
        val octavePart = normalized.removePrefix(notePart).toIntOrNull() ?: 4
        val noteIndex = noteNames.indexOf(notePart).takeIf { it >= 0 } ?: 9
        val midi = (octavePart + 1) * 12 + noteIndex

        return referenceForMidi(midi, referenceA4Hz)
    }

    fun prettyNoteName(noteName: String): String {
        return noteName
            .replace("A#", "B♭")
            .replace("C#", "D♭")
            .replace("D#", "E♭")
            .replace("F#", "G♭")
            .replace("G#", "A♭")
    }
}
