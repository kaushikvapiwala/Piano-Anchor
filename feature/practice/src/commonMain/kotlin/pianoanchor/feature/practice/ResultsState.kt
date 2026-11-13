package pianoanchor.feature.practice

import pianoanchor.core.model.Band

/** What the results screen shows for one finished run. */
public sealed interface ResultsState {
    /** The run is being read. */
    public data object Loading : ResultsState

    /** No run is stored under the id. */
    public data object NotFound : ResultsState

    /**
     * The run of [passageTitle], its [runNumber] among that passage's runs (oldest = 1), its [band], and its pitch:
     * [correctSteps] of [stepCount] steps, with [wrongNotes] wrong notes and [incompleteChords] chords incomplete.
     */
    public data class Ready(
        val passageTitle: String,
        val runNumber: Int,
        val band: Band,
        val correctSteps: Int,
        val stepCount: Int,
        val wrongNotes: Int,
        val incompleteChords: Int,
    ) : ResultsState
}
