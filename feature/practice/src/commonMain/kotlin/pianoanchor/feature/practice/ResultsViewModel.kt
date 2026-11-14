package pianoanchor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.analysis.PerformanceAnalyser
import pianoanchor.engine.analysis.PerformanceAnalysis
import pianoanchor.engine.analysis.TempoReference
import pianoanchor.engine.content.Passages
import pianoanchor.engine.grading.HesitationSummary
import pianoanchor.engine.grading.LengthSummary
import pianoanchor.engine.grading.ResultsSummary
import pianoanchor.engine.grading.TimingSummary

/**
 * The results of the run saved as [attemptId] in [attempts]: [ResultsState.Loading] until it is read, then
 * [ResultsState.Ready] or [ResultsState.NotFound], or [ResultsState.Unavailable] when the store can't be read. The
 * run number is one more than the passage's runs started before this one.
 */
public class ResultsViewModel(
    private val attemptId: String,
    private val attempts: AttemptRepositoryPort,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ResultsState>(ResultsState.Loading)
    public val state: StateFlow<ResultsState> = mutableState.asStateFlow()

    private var loading: Job? = null

    init {
        retry()
    }

    /**
     * Reads the run again ([ResultsState.Loading] meanwhile), e.g. after [ResultsState.Unavailable]; a read still
     * running is cancelled, so only the newest one sets the state.
     */
    public fun retry() {
        loading?.cancel()
        mutableState.value = ResultsState.Loading
        loading =
            viewModelScope.launch {
                mutableState.value =
                    try {
                        load()
                    } catch (_: StorageUnavailableException) {
                        ResultsState.Unavailable
                    }
            }
    }

    private suspend fun load(): ResultsState {
        val attempt = attempts.get(attemptId) ?: return ResultsState.NotFound
        val passage = Passages.byId(attempt.passageId)
        val summary = passage?.let { ResultsSummary.of(it, attempt, analyse(it, attempt)) }
        return ResultsState.Ready(
            passageTitle = passage?.title ?: attempt.passageId,
            runNumber = attempts.countStartedBefore(attempt.passageId, attempt.startedAt) + 1,
            band = attempt.band,
            // Wait mode moves on only when a step is right, so a finished run has every step correct.
            correctSteps = summary?.correctPitches ?: attempt.stepCount,
            stepCount = summary?.scoreNotes ?: attempt.stepCount,
            wrongNotes = attempt.wrongNotes,
            incompleteChords = attempt.incompleteChords,
            tempoBpm = attempt.tempoBpm,
            timing = summary?.timing?.let { timingTile(passage, it) },
            length = summary?.length?.let { lengthTile(passage, it) },
            hesitation = summary?.hesitation?.let { hesitationTile(it) },
        )
    }

    /** The analysis of a run with played notes (timed against its tempo, or untimed); null for older runs. */
    private fun analyse(
        passage: Passage,
        attempt: Attempt,
    ): PerformanceAnalysis? {
        if (attempt.playedNotes.isEmpty()) return null
        val tempo = attempt.tempoBpm?.let { TempoReference.Target(it, startNanos = 0) } ?: TempoReference.Untimed
        return PerformanceAnalyser.analyse(passage, attempt.playedNotes, tempo)
    }

    private fun timingTile(
        passage: Passage,
        timing: TimingSummary,
    ) = TimingTile(
        meanMs = timing.meanAbsDeviationMs,
        worst = timing.worst?.let { TimingWorst(it.kind == ErrorKind.LATE, noteNumber(passage, it.at)) },
    )

    private fun lengthTile(
        passage: Passage,
        length: LengthSummary,
    ) = LengthTile(
        heldRightPercent = length.heldRightPercent,
        worst =
            length.worst?.let {
                LengthWorst(it.kind == ErrorKind.TOO_LONG, noteNumber(passage, it.at), noteValue(passage, it.at))
            },
    )

    private fun hesitationTile(hesitation: HesitationSummary) =
        HesitationTile(
            count = hesitation.count,
            longest =
                hesitation.longestMs?.let { ms ->
                    hesitation.longestAt?.let { Pause((ms + MILLIS_PER_TENTH / 2) / MILLIS_PER_TENTH, it.pitch.name) }
                },
        )
}

private const val MILLIS_PER_TENTH = 100
private const val QUARTERS_IN_WHOLE = 4
private const val QUARTERS_IN_HALF = 2

/** The 1-based place of [ref] among the passage's score notes, in score order (step, then pitch). */
internal fun noteNumber(
    passage: Passage,
    ref: ScoreNoteRef,
): Int =
    passage.steps.take(ref.stepIndex).sumOf { it.notes.size } +
        passage.steps[ref.stepIndex].notes.count { it.pitch.number < ref.pitch.number } + 1

/** The written value of [ref]'s note when it is a whole, half or quarter note. */
internal fun noteValue(
    passage: Passage,
    ref: ScoreNoteRef,
): NoteValue? =
    when (
        passage.steps[ref.stepIndex]
            .notes
            .first { it.pitch == ref.pitch }
            .lengthTicks
    ) {
        QUARTERS_IN_WHOLE * TICKS_PER_QUARTER -> NoteValue.WHOLE
        QUARTERS_IN_HALF * TICKS_PER_QUARTER -> NoteValue.HALF
        TICKS_PER_QUARTER -> NoteValue.QUARTER
        else -> null
    }
