package pianoanchor.core.model

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
}
