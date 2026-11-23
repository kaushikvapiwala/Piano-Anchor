package pianoanchor.core.model

/**
 * One simple tone: [note] sounding from [startMs] (relative to the play call, at least 0) for [lengthMs] (above 0).
 * [velocity] (1-127) is how hard a piano is struck; null plays at the app's usual playback velocity. The speaker
 * ignores it.
 */
public data class Tone(
    val note: MidiNote,
    val startMs: Long,
    val lengthMs: Long,
    val velocity: Int? = null,
) {
    init {
        require(startMs >= 0) { "startMs must not be negative, was $startMs" }
        require(lengthMs > 0) { "lengthMs must be positive, was $lengthMs" }
        require(velocity == null || velocity in NoteOn.VELOCITY_RANGE) {
            "velocity must be in ${NoteOn.VELOCITY_RANGE}, was $velocity"
        }
    }
}
