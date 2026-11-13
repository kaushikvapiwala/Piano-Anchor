package pianoanchor.engine.grading

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.WrongNote
import pianoanchor.engine.Tunables
import kotlin.time.Instant

/** Grades a completed wait-mode run by pitch alone (`docs/algorithms/grading.md`). */
public object Grader {
    /**
     * steps / (steps + wrongNotes + incompleteChords): 1.0 for a run without errors. [steps] is the passage's step
     * count and must be positive; the error counts must not be negative.
     */
    public fun accuracy(
        steps: Int,
        wrongNotes: Int,
        incompleteChords: Int,
    ): Double {
        require(steps > 0) { "steps ($steps) must be positive" }
        require(wrongNotes >= 0 && incompleteChords >= 0) {
            "error counts ($wrongNotes wrong, $incompleteChords incomplete) must not be negative"
        }
        return steps.toDouble() / (steps + wrongNotes + incompleteChords)
    }

    /** The band of [accuracy] (0.0–1.0); a value exactly at a threshold gets the higher band. */
    public fun band(accuracy: Double): Band {
        require(accuracy in 0.0..1.0) { "accuracy ($accuracy) must be in 0.0..1.0" }
        return when {
            accuracy >= Tunables.BAND_CLEAN_MIN_ACCURACY -> Band.CLEAN
            accuracy >= Tunables.BAND_GOOD_MIN_ACCURACY -> Band.GOOD
            else -> Band.KEEP_PRACTISING
        }
    }

    /**
     * Builds the [Attempt] of a completed run of [passage]. [events] must end with [PassageComplete] (else
     * [IllegalArgumentException]); their timestamps are rebased so [startedAtNanos], the first NoteOn's onset, becomes
     * 0. [startedAt] is the wall clock at that note.
     */
    public fun attempt(
        id: String,
        passage: Passage,
        deviceId: String,
        startedAt: Instant,
        startedAtNanos: Long,
        events: List<MatchEvent>,
    ): Attempt {
        val complete = events.lastOrNull()
        require(complete is PassageComplete) { "a graded run must end with PassageComplete, got $complete" }
        val wrongNotes = events.count { it is WrongNote }
        val incompleteChords = events.count { it is ChordIncomplete }
        val accuracy = accuracy(passage.steps.size, wrongNotes, incompleteChords)
        return Attempt(
            id = id,
            passageId = passage.id,
            deviceId = deviceId,
            startedAt = startedAt,
            durationNanos = complete.timestampNanos - startedAtNanos,
            stepCount = passage.steps.size,
            wrongNotes = wrongNotes,
            incompleteChords = incompleteChords,
            accuracy = accuracy,
            band = band(accuracy),
            events = events.map { it.shiftedBy(-startedAtNanos) },
        )
    }

    private fun MatchEvent.shiftedBy(nanos: Long): MatchEvent {
        val t = timestampNanos + nanos
        return when (this) {
            is StepCorrect -> copy(timestampNanos = t)
            is WrongNote -> copy(timestampNanos = t)
            is ChordIncomplete -> copy(timestampNanos = t)
            is PassageComplete -> copy(timestampNanos = t)
        }
    }
}
