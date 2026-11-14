package pianoanchor.core.model

import kotlin.time.Instant

/** How well a run went, from its accuracy (thresholds in the engine's `Tunables`). */
public enum class Band { CLEAN, GOOD, KEEP_PRACTISING }

/**
 * One completed run of a passage; abandoned runs are not saved. [id] is caller-generated (a random UUID) and, like
 * [passageId] and [deviceId] (the instrument's stable device id), not blank. [startedAt] is the wall clock at the first
 * note and [durationNanos] the time from the first NoteOn to `PassageComplete`. [stepCount] is the passage's step
 * count; [accuracy] is in 0.0–1.0. [events] are the run's match events in order, with timestamps relative to the first
 * NoteOn, so the first is not negative. [tempoBpm] is the tempo of a timed (tempo-mode) run in
 * [MIN_TEMPO_BPM]..[MAX_TEMPO_BPM], null for wait mode. [playedNotes] are the take's notes, rebased so 0 is the written
 * time of tick 0 for a timed run and the first NoteOn for wait mode (empty for attempts stored before they were kept).
 */
public data class Attempt(
    val id: String,
    val passageId: String,
    val deviceId: String,
    val startedAt: Instant,
    val durationNanos: Long,
    val stepCount: Int,
    val wrongNotes: Int,
    val incompleteChords: Int,
    val accuracy: Double,
    val band: Band,
    val events: List<MatchEvent>,
    val tempoBpm: Int?,
    val playedNotes: List<PlayedNote>,
) {
    init {
        require(id.isNotBlank()) { "attempt id must not be blank" }
        require(passageId.isNotBlank()) { "attempt $id: passage id must not be blank" }
        require(deviceId.isNotBlank()) { "attempt $id: device id must not be blank" }
        require(durationNanos >= 0) { "attempt $id: duration ($durationNanos ns) must not be negative" }
        require(stepCount > 0) { "attempt $id: step count ($stepCount) must be positive" }
        require(wrongNotes >= 0) { "attempt $id: wrong notes ($wrongNotes) must not be negative" }
        require(incompleteChords >= 0) { "attempt $id: incomplete chords ($incompleteChords) must not be negative" }
        require(accuracy in 0.0..1.0) { "attempt $id: accuracy ($accuracy) must be in 0.0..1.0" }
        require(tempoBpm == null || tempoBpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM) {
            "attempt $id: tempo ($tempoBpm BPM) must be in $MIN_TEMPO_BPM..$MAX_TEMPO_BPM"
        }
        require((events.firstOrNull()?.timestampNanos ?: 0) >= 0) {
            "attempt $id: the first event must not be before the first note"
        }
        require(events.zipWithNext().all { (a, b) -> a.timestampNanos <= b.timestampNanos }) {
            "attempt $id: events must be in time order"
        }
    }
}
