package pianoanchor.engine.grading

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.WrongNote
import pianoanchor.engine.Tunables
import pianoanchor.engine.analysis.PerformanceAnalyser
import pianoanchor.engine.analysis.TempoReference
import kotlin.time.Instant

/** Findings of keys the score doesn't ask for: they enlarge a timed accuracy's denominator. */
private val STRAY_KINDS = setOf(ErrorKind.EXTRA_NOTE, ErrorKind.MISTOUCH)

/** Findings counted as wrong notes of a timed attempt. */
private val WRONG_KINDS = setOf(ErrorKind.WRONG_NOTE, ErrorKind.OCTAVE) + STRAY_KINDS

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
     * 0, and so are those of [playedNotes] (stored with the attempt). [startedAt] is the wall clock at that note.
     */
    public fun attempt(
        id: String,
        passage: Passage,
        deviceId: String,
        startedAt: Instant,
        startedAtNanos: Long,
        events: List<MatchEvent>,
        playedNotes: List<PlayedNote> = emptyList(),
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
            tempoBpm = null,
            playedNotes = playedNotes.map { it.shiftedBy(-startedAtNanos) },
        )
    }

    /**
     * Grades a timed take: [PerformanceAnalyser] with `TempoReference.Target(bpm, startNanos = 0)` over [notes], which
     * are already rebased so 0 is the written time of tick 0. Accuracy is the score notes without a finding over the
     * score notes plus the `EXTRA_NOTE` and `MISTOUCH` findings (`docs/algorithms/grading.md`); an extra or brushed key
     * adds to the denominator but doesn't spoil the score note it points at. The attempt keeps no match events;
     * [Attempt.durationNanos] is when the last key came up (0 if before tick 0).
     */
    public fun timedAttempt(
        id: String,
        passage: Passage,
        deviceId: String,
        startedAt: Instant,
        bpm: Int,
        notes: List<PlayedNote>,
    ): Attempt {
        val findings = PerformanceAnalyser.analyse(passage, notes, TempoReference.Target(bpm, startNanos = 0)).findings
        val stray = findings.filter { it.kind in STRAY_KINDS }
        val spoiled = (findings - stray.toSet()).mapTo(HashSet()) { it.at }
        val scoreNotes = passage.steps.sumOf { it.notes.size }
        val accuracy = (scoreNotes - spoiled.size).toDouble() / (scoreNotes + stray.size)
        return Attempt(
            id = id,
            passageId = passage.id,
            deviceId = deviceId,
            startedAt = startedAt,
            durationNanos = (notes.maxOfOrNull { it.upNanos } ?: 0).coerceAtLeast(0),
            stepCount = passage.steps.size,
            wrongNotes = findings.count { it.kind in WRONG_KINDS },
            incompleteChords = findings.count { it.kind == ErrorKind.INCOMPLETE_CHORD },
            accuracy = accuracy,
            band = band(accuracy),
            events = emptyList(),
            tempoBpm = bpm,
            playedNotes = notes,
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
