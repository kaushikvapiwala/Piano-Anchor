package pianoanchor.core.model

/** The order of played notes in a take: by key down, then by pitch. */
public val PLAYED_NOTE_ORDER: Comparator<PlayedNote> = compareBy<PlayedNote> { it.downNanos }.thenBy { it.pitch.number }

/**
 * A key as played: [pitch], onset [velocity] (1..127), key down and key up, and when it stopped sounding (key up,
 * or later if the sustain pedal held it). Times are monotonic nanoseconds with
 * [downNanos] <= [upNanos] <= [soundingEndNanos]; a zero-length note (down and up in the same message batch) is
 * allowed.
 */
public data class PlayedNote(
    val pitch: MidiNote,
    val velocity: Int,
    val downNanos: Long,
    val upNanos: Long,
    val soundingEndNanos: Long,
) {
    init {
        require(velocity in NoteOn.VELOCITY_RANGE) { "velocity must be in ${NoteOn.VELOCITY_RANGE}, was $velocity" }
        require(downNanos <= upNanos) { "${pitch.name}: key up ($upNanos) must not come before key down ($downNanos)" }
        require(upNanos <= soundingEndNanos) {
            "${pitch.name}: sounding end ($soundingEndNanos) must not come before key up ($upNanos)"
        }
    }

    /** This note with all three times moved by [nanos] (negative: earlier), e.g. to rebase a take. */
    public fun shiftedBy(nanos: Long): PlayedNote =
        copy(downNanos = downNanos + nanos, upNanos = upNanos + nanos, soundingEndNanos = soundingEndNanos + nanos)
}
