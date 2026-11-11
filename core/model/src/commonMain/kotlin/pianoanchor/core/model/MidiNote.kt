package pianoanchor.core.model

/**
 * A MIDI note number (0–127). Middle C is 60 and is named "C4" (scientific pitch notation).
 *
 * Names use sharps only; enharmonic spelling is a notation concern, not a pitch concern.
 */
public data class MidiNote(
    val number: Int,
) {
    init {
        require(number in RANGE) { "MIDI note must be in $RANGE, was $number" }
    }

    /** Scientific pitch name, e.g. 60 → "C4", 61 → "C#4", 21 → "A0". */
    val name: String
        get() = PITCH_CLASSES[number % SEMITONES_PER_OCTAVE] + (number / SEMITONES_PER_OCTAVE - 1)

    public companion object {
        public val RANGE: IntRange = 0..127
        public val MIDDLE_C: MidiNote = MidiNote(60)

        private const val SEMITONES_PER_OCTAVE = 12
        private val PITCH_CLASSES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }
}
