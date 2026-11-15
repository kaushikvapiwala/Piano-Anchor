package pianoanchor.core.model

/** One simple tone: [note] sounding from [startMs] (relative to the play call, at least 0) for [lengthMs] (above 0). */
public data class Tone(
    val note: MidiNote,
    val startMs: Long,
    val lengthMs: Long,
) {
    init {
        require(startMs >= 0) { "startMs must not be negative, was $startMs" }
        require(lengthMs > 0) { "lengthMs must be positive, was $lengthMs" }
    }
}
