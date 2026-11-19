package com.freetuner.app

/** Extra (Pro) tunings plus parsing/storage helpers for user-defined custom tunings. */
object Tunings {
    val pro: List<InstrumentPreset> = listOf(
        InstrumentPreset("Guitar", "Half step down", listOf("D#2", "G#2", "C#3", "F#3", "A#3", "D#4"), "Eb tuning, easier on the fingers"),
        InstrumentPreset("Guitar", "Full step down", listOf("D2", "G2", "C3", "F3", "A3", "D4"), "D standard"),
        InstrumentPreset("Guitar", "Drop C", listOf("C2", "G2", "C3", "F3", "A3", "D4"), "Heavy low tuning"),
        InstrumentPreset("Guitar", "Open D", listOf("D2", "A2", "D3", "F#3", "A3", "D4"), "Open slide tuning"),
        InstrumentPreset("Guitar", "Open E", listOf("E2", "B2", "E3", "G#3", "B3", "E4"), "Open slide tuning"),
        InstrumentPreset("Guitar", "7-string", listOf("B1", "E2", "A2", "D3", "G3", "B3", "E4"), "Seven-string standard"),
        InstrumentPreset("Guitar", "Baritone", listOf("B1", "E2", "A2", "D3", "F#3", "B3"), "Baritone B standard"),
        InstrumentPreset("Bass", "Drop D", listOf("D1", "A1", "D2", "G2"), "Four-string, low D"),
        InstrumentPreset("Bass", "5-string", listOf("B0", "E1", "A1", "D2", "G2"), "Five-string standard"),
        InstrumentPreset("Ukulele", "Baritone", listOf("D3", "G3", "B3", "E4"), "Baritone ukulele"),
        InstrumentPreset("Ukulele", "Low G", listOf("G3", "C4", "E4", "A4"), "Linear tuning"),
        InstrumentPreset("Banjo", "Double C", listOf("G4", "C3", "G3", "C4", "D4"), "Old-time tuning"),
    )

    const val CUSTOM_NAME = "Custom"
    private const val MAX_NAME = 20
    private const val MAX_STRINGS = 12
    private val noteRegex = Regex("^[A-G]#?[0-8]$")
    private val flats = mapOf("DB" to "C#", "EB" to "D#", "GB" to "F#", "AB" to "G#", "BB" to "A#")

    /** Parses "D2 A2 D3 G3 B3 E4" (spaces or commas; sharps with # or flats with b). Null if invalid. */
    fun parseNotes(text: String): List<String>? {
        val tokens = text.trim().split(Regex("[\\s,;]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > MAX_STRINGS) return null
        val notes = tokens.map { token ->
            val octave = token.takeLast(1)
            val pitch = token.dropLast(1).uppercase()
            val normalized = flats[pitch] ?: pitch
            normalized + octave
        }
        return if (notes.all { noteRegex.matches(it) }) notes else null
    }

    fun cleanName(name: String): String =
        name.trim().replace("|", "").replace(";", "").take(MAX_NAME)

    fun custom(name: String, notes: List<String>): InstrumentPreset =
        InstrumentPreset(CUSTOM_NAME, cleanName(name).ifEmpty { "My tuning" }, notes, "Your saved tuning")

    fun encode(presets: List<InstrumentPreset>): String =
        presets.joinToString(";") { "${it.tuningLabel}|${it.strings.joinToString(" ")}" }

    fun decode(raw: String): List<InstrumentPreset> =
        raw.split(";").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size != 2) return@mapNotNull null
            val notes = parseNotes(parts[1]) ?: return@mapNotNull null
            custom(parts[0], notes)
        }
}