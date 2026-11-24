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
import pianoanchor.engine.grading.ResultsWords
import pianoanchor.engine.grading.ResultsWordsRule
import pianoanchor.engine.grading.TimingSummary
import pianoanchor.engine.grading.Tip
import pianoanchor.engine.grading.noteNumber
import pianoanchor.engine.grading.noteValue
import pianoanchor.engine.guidance.SpotFinder
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.profile
import pianoanchor.engine.sight.SightLevels
import pianoanchor.engine.sight.SightReadingGenerator
import pianoanchor.engine.sight.SightStaircase

/**
 * The results of the run saved as [attemptId] in [attempts]: [ResultsState.Loading] until it is read, then
 * [ResultsState.Ready] or [ResultsState.NotFound], or [ResultsState.Unavailable] when the store can't be read. The
 * run number is one more than the passage's runs started before this one. [rung] is the rung the run was played on
 * (Practice passes it, so rungs that share a tempo stay distinct). The run is analysed with the strictness stored
 * in [settings] (Standard if unreadable), on [analysisDispatcher], off the main thread, so a long passage doesn't
 * stall the screen as it opens.
 *
 * A daily sight-reading exercise has no run number and no spot loop; [ResultsState.Ready.sight] says how it moved the
 * level, from the staircase over the attempts up to and before this one, capped at the level [learner] has opened.
 */
public class ResultsViewModel(
    private val attemptId: String,
    private val rung: TempoRung,
    private val attempts: AttemptRepositoryPort,
    private val settings: SettingsRepositoryPort,
    private val learner: LearnerModel,
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
        val passage = Curriculum.passage(attempt.passageId) ?: SightReadingGenerator.fromId(attempt.passageId)
        val sight = sightOutcome(attempt)
        val strictness = storedStrictness()
        val analysed =
            // A loop's notes and steps are the slice's, not the piece's: no analysis against the whole piece.
            passage?.takeIf { attempt.spot == null }?.let {
                withContext(analysisDispatcher) {
                    val analysis = analyse(it, attempt, strictness)
                    ResultsSummary.of(it, attempt, analysis) to spotOf(it, attempt, analysis?.findings.orEmpty())
                }
            }
        val summary = analysed?.first
        val words = wordsOf(summary ?: waitSummary(attempt), attempt, sight != null)
        return ResultsState.Ready(
            passageTitle = passage?.title ?: attempt.passageId,
            runNumber = runNumber(attempt, sight),
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
            sight = sight,
        )
    }

    /** One more than the passage's runs started before [attempt]; none for a sight-reading exercise. */
    private suspend fun runNumber(
        attempt: Attempt,
        sight: SightOutcome?,
    ): Int? = if (sight == null) attempts.countStartedBefore(attempt.passageId, attempt.startedAt) + 1 else null

    /** How [attempt] moved the sight-reading level; null when it is not a sight-reading exercise. */
    private suspend fun sightOutcome(attempt: Attempt): SightOutcome? {
        val (level, _) = SightReadingGenerator.parse(attempt.passageId) ?: return null
        val open = SightLevels.open(learner.learnedSkills())
        val all = attempts.attemptsWithPassagePrefix(SightReadingGenerator.ID_PREFIX)
        val upTo = all.takeWhile { it.id != attempt.id } + attempt
        val before = upTo.dropLast(1)
        val now = SightStaircase.level(upTo, open)
        val change =
            when {
                before.any { it.passageId == attempt.passageId } -> SightChange.REPLAY
                now > SightStaircase.level(before, open) -> SightChange.UP
                now < SightStaircase.level(before, open) -> SightChange.DOWN
                else -> SightChange.STAYS
            }
        return SightOutcome(level, change, now)
    }

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
        val offered =
            attempt.mode == RunMode.PRACTICE && attempt.band != Band.CLEAN &&
                SightReadingGenerator.parse(attempt.passageId) == null
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

/** What Results say; a sight-reading run has no tempo to try next, so it gets no tip in that case. */
private fun wordsOf(
    summary: ResultsSummary,
    attempt: Attempt,
    sight: Boolean,
): ResultsWords {
    val words = ResultsWordsRule.of(summary, attempt.tempoBpm, attempt.band)
    return if (sight && words.tip == Tip.NEXT_TEMPO) words.copy(tip = null) else words
}
