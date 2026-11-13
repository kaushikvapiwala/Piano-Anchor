package pianoanchor.feature.practice

import pianoanchor.core.model.MidiNote

private const val SEMITONES_PER_OCTAVE = 12
private const val LETTERS_PER_OCTAVE = 7
private const val KEY_STRIP_OCTAVES = 2

/** Letter index (C = 0 … B = 6) of each pitch class; a sharp shares its natural's letter. */
private val LETTER_OF_PITCH_CLASS = listOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
private val BLACK_PITCH_CLASSES = setOf(1, 3, 6, 8, 10)

/** E4, the treble staff's bottom line, as a diatonic index (octave × 7 + letter). */
private val BOTTOM_LINE = diatonicIndex(MidiNote(64))

/**
 * The key strip's note numbers: two octaves from the C at or below [lowestNote], the passage's lowest note
 * (C4 → C4–B5), moved down an octave at a time where needed so they stay within MIDI's 0–127. Notes more than two
 * octaves above that C have no key (built-in passages fit).
 */
internal fun keyStripRange(lowestNote: MidiNote): IntRange {
    val lowest = lowestNote.number
    val highestStart = MidiNote.RANGE.last + 1 - KEY_STRIP_OCTAVES * SEMITONES_PER_OCTAVE
    val start = minOf(lowest, highestStart).let { it - it % SEMITONES_PER_OCTAVE }
    return start until start + KEY_STRIP_OCTAVES * SEMITONES_PER_OCTAVE
}

/** Whether this is a black key. */
internal val MidiNote.isBlack: Boolean get() = number % SEMITONES_PER_OCTAVE in BLACK_PITCH_CLASSES

/**
 * Half staff spaces above the treble staff's bottom line (E4 = 0, F4 = 1, C4 = −2, F5 = 8). A sharp sits where its
 * natural does.
 */
internal val MidiNote.staffPosition: Int get() = diatonicIndex(this) - BOTTOM_LINE

private fun diatonicIndex(note: MidiNote): Int =
    note.number / SEMITONES_PER_OCTAVE * LETTERS_PER_OCTAVE + LETTER_OF_PITCH_CLASS[note.number % SEMITONES_PER_OCTAVE]
