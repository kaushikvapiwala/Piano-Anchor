package pianoanchor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Finding
import pianoanchor.core.model.Passage
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.Strictness
import pianoanchor.core.model.StrictnessProfile
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.engine.analysis.PerformanceAnalyser
import pianoanchor.engine.analysis.PerformanceAnalysis
import pianoanchor.engine.analysis.TempoReference
import pianoanchor.engine.calibration.LatencyCalibration
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.grading.HesitationSummary
import pianoanchor.engine.grading.LengthSummary
import pianoanchor.engine.grading.ResultsSummary
import pianoanchor.engine.grading.ResultsWordsRule
import pianoanchor.engine.grading.TimingSummary
import pianoanchor.engine.grading.noteNumber
import pianoanchor.engine.grading.noteValue
import pianoanchor.engine.guidance.SpotFinder
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.profile

/**
 * The results of the run saved as [attemptId] in [attempts]: [ResultsState.Loading] until it is read, then
 * [ResultsState.Ready] or [ResultsState.NotFound], or [ResultsState.Unavailable] when the store can't be read. The
 * run number is one more than the passage's runs started before this one. [rung] is the rung the run was played on
 * (Practice passes it, so rungs that share a tempo stay distinct). The run is analysed with the strictness stored
 * in [settings] (Standard if unreadable), on [analysisDispatcher], off the main thread, so a long passage doesn't
 * stall the screen as it opens.
 */
public class ResultsViewModel(
    private val attemptId: String,
    private val rung: TempoRung,
    private val attempts: AttemptRepositoryPort,
    private val settings: SettingsRepositoryPort,
    private val analysisDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
        val passage = Curriculum.passage(attempt.passageId)
        val strictness = storedStrictness()
        val analysed =
            passage?.let {
                withContext(analysisDispatcher) {
                    val analysis = analyse(it, attempt, strictness)
                    ResultsSummary.of(it, attempt, analysis) to spotOf(it, attempt, analysis?.findings.orEmpty())
                }
            }
        val summary = analysed?.first
        val words = ResultsWordsRule.of(summary ?: waitSummary(attempt), attempt.tempoBpm, attempt.band)
        return ResultsState.Ready(
            passageTitle = passage?.title ?: attempt.passageId,
            runNumber = attempts.countStartedBefore(attempt.passageId, attempt.startedAt) + 1,
            band = attempt.band,
            // Wait mode moves on only when a step is right, so a finished run has every step correct.
            correctSteps = summary?.correctPitches ?: attempt.stepCount,
            stepCount = summary?.scoreNotes ?: attempt.stepCount,
            wrongNotes = summary?.wrongNotes ?: attempt.wrongNotes,
            incompleteChords = summary?.incompleteChords ?: attempt.incompleteChords,
            missedNotes = summary?.missedNotes ?: 0,
            tempoBpm = attempt.tempoBpm,
            timing = summary?.timing?.let { timingTile(passage, it, calibrated = attempt.latencyOffsetNanos != null) },
            length = summary?.length?.let { lengthTile(passage, it) },
            hesitation = summary?.hesitation?.let { hesitationTile(it) },
            mode = attempt.mode,
            spot = analysed?.second,
            rung = rung,
            words = words,
            shiftBeats = shiftBeats(summary?.timing, attempt.tempoBpm),
        )
    }

    /** What an attempt with no analysis says: its own counts, no timing, length or pauses. */
    private fun waitSummary(attempt: Attempt) =
        ResultsSummary(
            correctPitches = attempt.stepCount,
            scoreNotes = attempt.stepCount,
            timing = null,
            length = null,
            hesitation = null,
            wrongNotes = attempt.wrongNotes,
            incompleteChords = attempt.incompleteChords,
        )

    private fun shiftBeats(
        timing: TimingSummary?,
        tempoBpm: Int?,
    ): Double =
        if (timing == null ||
            tempoBpm == null
        ) {
            0.0
        } else {
            ResultsWordsRule.shiftBeats(timing.medianSignedDeviationMs, tempoBpm)
        }

    /** The learner's strictness profile, read on each load; Standard when the settings can't be read. */
    private suspend fun storedStrictness(): StrictnessProfile =
        try {
            settings.settings
                .first()
                .strictness
                .profile()
        } catch (_: StorageUnavailableException) {
            Strictness.STANDARD.profile()
        }

    /** The bars to offer as a spot loop: none for a perform take or a CLEAN run (`docs/algorithms/guidance.md`). */
    private fun spotOf(
        passage: Passage,
        attempt: Attempt,
        findings: List<Finding>,
    ): BarRange? {
        val offered = attempt.mode == RunMode.PRACTICE && attempt.band != Band.CLEAN
        return if (offered) SpotFinder.worst(passage, attempt, findings) else null
    }

    /** The analysis of a timed run, or of a wait-mode run with played notes; null for older wait-mode runs. */
    private fun analyse(
        passage: Passage,
        attempt: Attempt,
        strictness: StrictnessProfile,
    ): PerformanceAnalysis? {
        if (attempt.tempoBpm == null && attempt.playedNotes.isEmpty()) return null
        val tempo = attempt.tempoBpm?.let { TempoReference.Target(it, startNanos = 0) } ?: TempoReference.Untimed
        val notes = LatencyCalibration.corrected(attempt.playedNotes, attempt.latencyOffsetNanos)
        return PerformanceAnalyser.analyse(passage, notes, tempo, strictness)
    }

    private fun timingTile(
        passage: Passage,
        timing: TimingSummary,
        calibrated: Boolean,
    ) = TimingTile(
        meanMs = timing.meanAbsDeviationMs,
        calibrated = calibrated,
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
