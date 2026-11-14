package pianoanchor.feature.practice

import pianoanchor.core.model.Band
import pianoanchor.engine.grading.NoteValue

/** What the results screen shows for one finished run. */
public sealed interface ResultsState {
    /** The run is being read. */
    public data object Loading : ResultsState

    /** No run is stored under the id. */
    public data object NotFound : ResultsState

    /** The store could not be read; the screen offers Retry. */
    public data object Unavailable : ResultsState

    /**
     * The run of [passageTitle], its [runNumber] among that passage's runs (oldest = 1), its [band], and its pitch:
     * [correctSteps] of [stepCount] (steps in wait mode, score notes in a timed run), with [wrongNotes] wrong notes,
     * [missedNotes] missed notes and [incompleteChords] chords incomplete. A timed run has a [tempoBpm] and, when a
     * note had its exact pitch, a [timing] and [length] tile; [hesitation] is null for a run stored before played
     * notes were kept.
     */
    public data class Ready(
        val passageTitle: String,
        val runNumber: Int,
        val band: Band,
        val correctSteps: Int,
        val stepCount: Int,
        val wrongNotes: Int,
        val incompleteChords: Int,
        val missedNotes: Int = 0,
        val tempoBpm: Int? = null,
        val timing: TimingTile? = null,
        val length: LengthTile? = null,
        val hesitation: HesitationTile? = null,
    ) : ResultsState
}

/** The Timing tile: the mean deviation of the notes in ms, and the [worst] early or late note, null when in time. */
public data class TimingTile(
    val meanMs: Int,
    val worst: TimingWorst?,
) {
    /** Orange when any note was early or late. */
    val offBeat: Boolean get() = worst != null
}

/** A note that was [late] (else early); [noteNumber] counts score notes from 1. */
public data class TimingWorst(
    val late: Boolean,
    val noteNumber: Int,
)

/** The Note length tile: the percentage of notes [heldRightPercent], and the [worst] one, null when all were right. */
public data class LengthTile(
    val heldRightPercent: Int,
    val worst: LengthWorst?,
)

/** A note held too long (else too short); named by [value] when it is a whole, half or quarter note. */
public data class LengthWorst(
    val tooLong: Boolean,
    val noteNumber: Int,
    val value: NoteValue?,
)

/** The Hesitation tile: how many pauses, and the [longest]. */
public data class HesitationTile(
    val count: Int,
    val longest: Pause?,
)

/** A pause of [tenthsOfSecond] before the note called [noteName]. */
public data class Pause(
    val tenthsOfSecond: Int,
    val noteName: String,
)
