package pianoanchor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.HintLevel
import pianoanchor.core.model.LinkResumed
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Passage
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.Strictness
import pianoanchor.core.model.StrictnessProfile
import pianoanchor.core.model.WrongNote
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.CalibrationRepositoryPort
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MetronomePort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.analysis.PlayedNotes
import pianoanchor.engine.analysis.releasesAllKeys
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.grading.Grader
import pianoanchor.engine.guidance.HintDelay
import pianoanchor.engine.guidance.HintFading
import pianoanchor.engine.guidance.wrongKeyHint
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.learner.learnerDay
import pianoanchor.engine.lesson.NoteLabelsUseCase
import pianoanchor.engine.matcher.LiveVerdict
import pianoanchor.engine.matcher.MatchState
import pianoanchor.engine.matcher.TempoJudge
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.matcher.WaitModeMatcher
import pianoanchor.engine.matcher.beatNanos
import pianoanchor.engine.matcher.offersSlowerRung
import pianoanchor.engine.midi.defaultLinkLatencyNanos
import pianoanchor.engine.midi.measureLinkLatencyNanos
import pianoanchor.engine.profile
import pianoanchor.engine.sight.SightReadingGenerator
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/**
 * Practice of [passage] on [device]. Wait mode follows the piano's notes with [WaitModeMatcher]; a timed rung
 * ([selectRung]) arms itself (the link delay is measured, then after `Tunables.TEMPO_AUTO_START_DELAY_MS` it counts
 * in one bar with [metronome]), then moves the cursor with [time] and judges each note live ([TempoJudge]). The first
 * wrong or missed note stops the run, says why for `Tunables.TEMPO_RESTART_PAUSE_MS` and arms it again from the top;
 * after `Tunables.TEMPO_RESTARTS_BEFORE_OFFER` such restarts in a row on a rung the pause offers a slower rung
 * ([slowerRung], [keepTrying]) instead. It shows progress and the live keys, and when the run is done grades it
 * ([Grader]), saves it to [attempts] with its played notes and sets [PracticeState.finishedAttemptId]. A run that
 * ends early (disconnect, [restart], [selectRung], leaving the screen) is discarded. When the store is unavailable
 * the graded attempt is kept with [PracticeState.saveFailed] until [retrySave] or [discardRun]. [clock] gives the
 * attempt's start time (at the first note); [learner] records each saved attempt as a cold check on the learner day
 * ([zone]); [newId] the attempt's id; [settings] keeps whether the metronome clicks: it seeds
 * [PracticeState.clickOn] and [toggleClick] writes it back (a store that fails leaves the choice in force for this
 * screen only).
 *
 * A timed run subtracts the piano's link delay from played times (`docs/algorithms/latency-calibration.md`): the
 * offset a tap calibration stored in [calibration], else the one [measureLinkLatencyNanos] finds over [output] before
 * the run opens the input (measured once per ViewModel), else the default of the piano's transport. Wait mode
 * measures nothing. The attempt records the offset used.
 * The ViewModel starts on [initialRung] (wait mode by default) in [initialMode] (practice by default); a
 * spot loop starts one rung below [initialRung]. A spot loop's [passage] is a slice of the piece [pieceId]; its
 * attempts are saved under [pieceId] with [spot] set, so a spot loop must name its piece (else
 * [IllegalArgumentException]).
 *
 * [selectMode] switches between practice and perform (`docs/algorithms/guidance.md`). A perform run needs a timed
 * rung: no hint ever shows, the live verdicts (marks, tags, the key strip's text, wrong-key colours) stay hidden, and
 * the attempt is saved as [RunMode.PERFORM] with `hintShown` false. WAIT always practises.
 *
 * With a [player] the passage plays once, at its written tempo, before the first run of a passage never played
 * (hear it first; [skipListening] stops it) and again on [listen] while no run is under way. A wrong key in wait mode
 * sets [PracticeState.hint] and the glow keys (`docs/algorithms/wrong-key-hint.md`).
 *
 * A sight-reading [passage] (its id parses with `SightReadingGenerator.parse`) always practises in wait mode from the
 * staff alone: hint level OFF, no Hint button, no hear-first or Listen, no tempo rungs or perform mode, whatever
 * [initialRung] and [initialMode] say. The wrong-key line still shows.
 *
 * A [placement] run (a piece of the onboarding's placement test, `docs/algorithms/learner-model.md`) runs the same
 * way, and its attempt is saved as usual but records no skill review, passed or failed: the onboarding places the
 * skills of the passed units itself, which only works for skills with no state yet.
 * [PracticeState.finishedBand] is the band of the saved attempt.
 *
 * An [unaided] run (the whole-passage wait-mode run of a lesson's Use step, `docs/course/beginner-course.md`,
 * "Mastery"; a timed rung or spot loop keeps its hints) has hint level OFF whatever the passage's history says: no key
 * band, no Unit 0 glow of the next key, no Hint button. The wrong-key line still
 * shows (it is feedback, not a hint), and the attempt is saved with `hintShown` false, so a clean run passes the
 * concept.
 */
@Suppress("TooManyFunctions")
public class PracticeViewModel(
    private val device: MidiDevice,
    private val passage: Passage,
    private val input: MidiInputPort,
    private val output: MidiOutputPort,
    private val attempts: AttemptRepositoryPort,
    private val calibration: CalibrationRepositoryPort,
    private val metronome: MetronomePort,
    private val time: ClockPort,
    private val clock: Clock,
    private val learner: LearnerModel,
    private val zone: TimeZone,
    private val settings: SettingsRepositoryPort,
    initialRung: TempoRung = TempoRung.WAIT,
    initialMode: RunMode = RunMode.PRACTICE,
    private val spot: BarRange? = null,
    private val player: PassagePlayer? = null,
    private val pieceId: String = passage.id,
    private val placement: Boolean = false,
    private val unaided: Boolean = false,
    private val newId: () -> String,
) : ViewModel() {
    /**
     * Whether the run is plain: a sight-reading exercise (`docs/algorithms/sight-reading.md`) or a placement piece.
     * Both are played in wait mode from the staff alone, with no hint, hear-first or Listen.
     */
    private val plain = placement || SightReadingGenerator.parse(passage.id) != null

    /**
     * Whether no hint shows in the run now: a plain run, or the whole-passage wait-mode run of a lesson's Use step
     * ([unaided]; a timed rung or a spot loop opened from it keeps its hints).
     */
    private val noHints: Boolean get() = plain || mutableState.value.unaided

    private val mutableState =
        MutableStateFlow(
            PracticeState
                .start(passage)
                .copy(
                    spotLoop = spot?.let { SpotLoopState(it, initialRung) },
                    staffHidden = isUnitZero(passage),
                    sightReading = plain,
                    unaided = unaided && spot == null && initialRung == TempoRung.WAIT,
                ),
        )
    public val state: StateFlow<PracticeState> = mutableState.asStateFlow()

    private var run: Job? = null
    private var linkOffset: Long? = null
    private var flashTimer: Job? = null

    /** The passage playing for the learner, if any. */
    private var demo: Job? = null

    /** True until the screen has decided whether this passage plays itself first (once per screen). */
    private var hearFirstPending = spot == null && !plain

    /** The graded attempt whose save failed, until it is saved or discarded; null while a retry runs. */
    private var unsaved: Attempt? = null

    /** True once the learner toggled the click here: the stored choice, if read later, no longer applies. */
    private var clickToggled = false

    /** The key-hint level the passage starts at (saved with the attempt) and the delay of its Delayed hints. */
    private var hintLevel = HintLevel.FULL
    private var hintDelayMillis = Tunables.HINT_DELAY_DEFAULT_MS

    /** The level the run in progress actually shows hints at: [hintLevel], or OFF in a perform take. */
    private var runHintLevel = HintLevel.FULL

    /** The level and delay of the first successful history read, null until then. */
    private var hintCache: Pair<HintLevel, Long>? = null

    /** The strictness profile runs are judged and graded with, read from the settings when the screen opens. */
    private var strictness: StrictnessProfile = Strictness.STANDARD.profile()

    /** Whether any hint showed in the run in progress: FULL, a fired Delayed hint, the Hint button, the Unit 0 glow. */
    private var hintShown = true
    private var hintTimer: Job? = null

    /** The click pattern of the timed run in progress, null outside one. */
    private var pattern: ClickPattern? = null

    /** Mistake restarts in a row on this rung and mode; a clean finish, a rung or a mode change resets it. */
    private var restartsInARow = 0

    init {
        require(spot == null || pieceId != passage.id) { "a spot loop needs the id of its piece" }
        viewModelScope.launch { refreshDoneRungs() }
        viewModelScope.launch { seedClickOn() }
        viewModelScope.launch { loadNoteLabels() }
        start(if (spot == null) initialRung else initialRung.below(), initialMode)
    }

    /** Discards the run and starts over on the same rung, reopening the piano once the old run has closed it. */
    public fun restart() {
        start(mutableState.value.rung, mutableState.value.mode)
    }

    /**
     * Discards any run and goes to [rung], ready to start: wait mode as before, a timed rung arming itself. The
     * piano is reopened once the old run has closed it (one reader per input).
     */
    public fun selectRung(rung: TempoRung) {
        restartsInARow = 0
        // A spot loop's clean streak counts loops at one rung: a new rung starts it again.
        mutableState.update { it.copy(spotLoop = it.spotLoop?.restarted()) }
        start(rung, mutableState.value.mode)
    }

    /**
     * Discards any run and switches to [mode], ready to start, like [selectRung]. Perform is ignored while the rung is
     * WAIT or a spot loop runs, and so is the mode already selected.
     */
    public fun selectMode(mode: RunMode) {
        val current = mutableState.value
        if (spot != null || mode == current.mode || !current.rung.canPerform) return
        restartsInARow = 0
        start(current.rung, mode)
    }

    /** The "Slower" button of the too-hard card: the rung below the current one (Wait below the lowest timed rung). */
    public fun slowerRung() {
        if (!mutableState.value.offerSlower) return
        selectRung(mutableState.value.rung.below())
    }

    /** The "Keep trying" button of the too-hard card: arms the run again on the same rung, counting afresh. */
    public fun keepTrying() {
        if (!mutableState.value.offerSlower) return
        restartsInARow = 0
        restart()
    }

    private fun start(
        wanted: TempoRung,
        requested: RunMode,
    ) {
        val rung = if (plain) TempoRung.WAIT else wanted
        val mode = if (!rung.canPerform || spot != null || plain) RunMode.PRACTICE else requested
        flashTimer?.cancel()
        hintTimer?.cancel()
        demo?.cancel()
        unsaved = null
        mutableState.update {
            PracticeState
                .start(passage)
                .copy(
                    rung = rung,
                    mode = mode,
                    doneRungs = it.doneRungs,
                    clickOn = it.clickOn,
                    spotLoop = it.spotLoop,
                    canListen = player != null && !plain,
                    sightReading = plain,
                    unaided = unaided && spot == null && rung == TempoRung.WAIT,
                    staffHidden = it.staffHidden,
                    // Hidden until `loadHints` reads the run's level, so a slow store can't flash the band at FULL.
                    hintVisible = false,
                )
        }
        val previous = run
        run =
            viewModelScope.launch {
                previous?.cancelAndJoin()
                loadHints()
                hearFirst()
                val bpm = rung.bpm(passage)
                if (bpm == null) WaitRun().play() else playTimed(bpm)
            }
    }

    /** Plays the passage again for the learner (the Listen button), unless a run is under way or it is playing. */
    public fun listen() {
        if (!mutableState.value.showsListen) return
        demo = startDemo()
    }

    /** Stops the passage playing for the learner (the Skip button). */
    public fun skipListening() {
        demo?.cancel()
    }

    /** Hear it first: before the first run of a passage with no attempt yet, plays it once and waits for it to end. */
    private suspend fun hearFirst() {
        if (!hearFirstPending || player == null) return
        hearFirstPending = false
        val never =
            try {
                attempts.lastPlayed(pieceId) == null
            } catch (_: StorageUnavailableException) {
                false // can't tell: don't make a returning learner wait
            }
        if (!never) return
        val job = startDemo()
        demo = job
        job.join()
    }

    /** Plays the passage with the cursor and keys following the sound; the screen is back to rest when it ends. */
    private fun startDemo(): Job {
        val previous = demo
        return viewModelScope.launch {
            previous?.cancelAndJoin()
            mutableState.update { it.copy(listening = true) }
            try {
                coroutineScope {
                    launch { followDemo() }
                    checkNotNull(player).play(passage.tones())
                }
            } finally {
                mutableState.update { it.copy(listening = false, demoStep = null, demoKeys = emptySet()) }
            }
        }
    }

    /** Moves the demo cursor and its lit keys to each step as its onset comes. */
    private suspend fun followDemo() {
        var at = 0L
        passage.steps.forEachIndexed { i, step ->
            val onset = step.onsetTicks.toMillis(passage.tempoBpm)
            delay(onset - at)
            at = onset
            mutableState.update { it.copy(demoStep = i, demoKeys = step.pitches) }
        }
    }

    /** Shows the key band on the waiting step, for that step only (the Hint button). */
    public fun showHint() {
        if (!mutableState.value.showsVerdicts || noHints) return
        hintShown = true
        mutableState.update { it.copy(hintVisible = true) }
    }

    /**
     * Works out the run's hint level and delay from the passage's stored attempts; a store that can't be read gives
     * the defaults (FULL) for this start only. A successful read is kept for later starts, since the history can't
     * change within this screen (a saved whole-passage run leaves it, and the history read leaves loop attempts out); a
     * failed save clears it, as that save may have reached the store.
     */
    private suspend fun loadHints() {
        val known = hintCache
        if (noHints) {
            hintLevel = HintLevel.OFF
        } else if (known != null) {
            hintLevel = known.first
            hintDelayMillis = known.second
        } else {
            val history =
                try {
                    attempts.recent(pieceId, Tunables.HINT_FADE_HISTORY).first()
                } catch (_: StorageUnavailableException) {
                    null
                }
            val runs = history.orEmpty()
            hintLevel = HintFading.level(runs.asReversed())
            hintDelayMillis = HintDelay.millis(runs)
            if (history != null) hintCache = hintLevel to hintDelayMillis
        }
        // A perform run gets no hint at all, whatever the level the passage would start at.
        runHintLevel = if (mutableState.value.showsVerdicts) hintLevel else HintLevel.OFF
        val aided = runHintLevel == HintLevel.FULL
        // A Unit 0 run glows every next key at any level: that is a hint too, unless the run is unaided (no glow).
        val glows = mutableState.value.let { it.staffHidden && it.showsVerdicts && !it.unaided }
        hintShown = aided || glows
        mutableState.update { it.copy(hintLevel = hintLevel, hintVisible = aided) }
    }

    /**
     * A new step is waiting: its band shows at once when the run's level ([runHintLevel]) is FULL, otherwise not, and
     * at DELAYED shows after [hintDelayMillis] unless a key press cancels the timer first. A timed step already
     * answered (played early) gets no timer.
     */
    private fun startHintStep(answered: Boolean = false) {
        hintTimer?.cancel()
        mutableState.update { it.copy(hintVisible = runHintLevel == HintLevel.FULL) }
        if (runHintLevel != HintLevel.DELAYED || answered) return
        hintTimer =
            viewModelScope.launch {
                delay(hintDelayMillis)
                showHint()
            }
    }

    /**
     * Switches the metronome on or off, also while a run is in progress: the pattern keeps its timing, so turning the
     * click back on rejoins the beat. The choice is stored in the settings; if that fails it still holds until this
     * screen closes.
     */
    public fun toggleClick() {
        val on = !mutableState.value.clickOn
        clickToggled = true
        mutableState.update { it.copy(clickOn = on) }
        viewModelScope.launch {
            try {
                // Stores what the learner now sees, even if the store held something else.
                settings.update { it.copy(clickOn = on) }
            } catch (_: StorageUnavailableException) {
                // Not surfaced here: the choice stays in force for this session.
            }
        }
        val running = pattern ?: return
        viewModelScope.launch {
            if (on) running.start(metronome) else metronome.stop()
            // The run ended (and stopped the click) while this start ran: don't leave it clicking.
            if (on && pattern !== running) metronome.stop()
        }
    }

    /** Letters the keys as the learner's setting and progress say; the strip shows none until this has read them. */
    private suspend fun loadNoteLabels() {
        val labels = NoteLabelsUseCase(settings, attempts, learner, zone).load()
        mutableState.update { it.copy(noteLabels = labels) }
    }

    /**
     * Takes the stored click choice as the starting one, unless the learner toggled the click before it was read, and
     * the stored strictness for the runs; a store that can't be read leaves the defaults.
     */
    private suspend fun seedClickOn() {
        val stored =
            try {
                settings.settings.first()
            } catch (_: StorageUnavailableException) {
                return
            }
        strictness = stored.strictness.profile()
        if (clickToggled) return
        mutableState.update { it.copy(clickOn = stored.clickOn) }
        // A timed run that started before the read returned began clicking on the default: silence it.
        if (!stored.clickOn && pattern != null) metronome.stop()
    }

    /**
     * Saves again the attempt whose save failed, under the same id. An id already stored means an earlier save did
     * commit, so it counts as saved. Ignored while a retry runs or when nothing is unsaved.
     */
    public fun retrySave() {
        val attempt = unsaved ?: return
        unsaved = null // a tap while this retry runs finds nothing to retry
        run =
            viewModelScope.launch {
                try {
                    attempts.save(attempt)
                } catch (_: IllegalArgumentException) {
                    // Already stored: an earlier save committed after all.
                } catch (_: StorageUnavailableException) {
                    return@launch settle(attempt, saved = false)
                }
                savedSettle(attempt)
            }
    }

    /** Drops the attempt whose save failed and starts a fresh run on the same piano, like Try again. */
    public fun discardRun(): Unit = restart()

    /**
     * Ends the run and returns once the piano is closed, so the next screen can open it (one reader per input).
     * A run not yet saved is discarded.
     */
    public suspend fun close() {
        flashTimer?.cancel()
        hintTimer?.cancel()
        demo?.cancelAndJoin()
        run?.cancelAndJoin()
    }

    private suspend fun playTimed(bpm: Int) {
        val stored =
            try {
                calibration.latencyOffsetNanos(device.id)
            } catch (_: StorageUnavailableException) {
                null // can't tell: measure rather than block the run
            }
        runTimed(bpm, stored ?: linkOffsetNanos())
    }

    /** The measured link delay, else the transport's default; found once, before the first timed run's reader opens. */
    private suspend fun linkOffsetNanos(): Long =
        linkOffset ?: (
            measureLinkLatencyNanos(device, input, output, time) ?: defaultLinkLatencyNanos(device.transport)
        ).also { linkOffset = it }

    private suspend fun runTimed(
        bpm: Int,
        offsetNanos: Long,
    ) {
        val timed = TimedRun(bpm, offsetNanos)
        val outcome =
            try {
                timed.play()
            } finally {
                pattern = null
                withContext(NonCancellable) { metronome.stop() }
            }
        when (outcome) {
            RunOutcome.Dropped -> {
                return mutableState.update { it.disconnected() }
            }

            is RunOutcome.Stopped -> {
                return afterMistake(outcome.reason)
            }

            RunOutcome.Completed -> {}
        }
        restartsInARow = 0
        mutableState.update { it.copy(phase = RunPhase.FINISHED, beat = 0, playNow = false) }
        save(
            Grader.timedAttempt(
                id = newId(),
                passage = passage,
                deviceId = device.id,
                startedAt = checkNotNull(timed.startedAt),
                bpm = bpm,
                notes = timed.notes(),
                latencyOffsetNanos = offsetNanos,
                strictness = strictness,
            ),
        )
    }

    /**
     * A mistake stopped the run and discarded its take: says why, then arms the run again from the top after
     * `Tunables.TEMPO_RESTART_PAUSE_MS`, unless this is the restart that makes the too-hard card show instead.
     */
    private suspend fun afterMistake(reason: RestartReason) {
        hintTimer?.cancel()
        restartsInARow++
        val offer = offersSlowerRung(restartsInARow)
        mutableState.update {
            it.copy(restartReason = reason, offerSlower = offer, phase = RunPhase.READY, beat = 0, playNow = false)
        }
        if (offer) return
        delay(Tunables.TEMPO_RESTART_PAUSE_MS)
        restart()
    }

    private suspend fun save(graded: Attempt) {
        hintTimer?.cancel()
        val attempt =
            graded.copy(
                hintLevel = hintLevel,
                hintShown = hintShown,
                mode = mutableState.value.mode,
                passageId = pieceId,
                spot = spot,
            )
        try {
            attempts.save(attempt)
        } catch (_: StorageUnavailableException) {
            return settle(attempt, saved = false)
        }
        savedSettle(attempt)
    }

    /** After a successful save: records the learning, refreshes the done rungs and finishes the run. */
    private suspend fun savedSettle(attempt: Attempt) {
        recordLearning(attempt)
        refreshDoneRungs()
        settle(attempt, saved = true)
    }

    /**
     * Records the saved [attempt] as a cold check (none for a placement piece). The run is already safe, so a
     * learner store that can't be written is skipped: a replay or the next cold check recovers
     * (`docs/algorithms/learner-model.md`).
     */
    private suspend fun recordLearning(attempt: Attempt) {
        if (placement) return
        try {
            learner.record(passage, attempt, learnerDay(attempt.startedAt, zone))
        } catch (_: StorageUnavailableException) {
            // Skipped on purpose, see above.
        }
    }

    /** Marks the rungs whose tempo has a clean timed attempt; a store that can't be read leaves them as they are. */
    private suspend fun refreshDoneRungs() {
        val tempos =
            try {
                attempts.cleanTempos(pieceId)
            } catch (_: StorageUnavailableException) {
                return
            }
        val done = TempoRung.entries.filterTo(HashSet()) { rung -> rung.bpm(passage)?.let { it in tempos } == true }
        mutableState.update { it.copy(doneRungs = done) }
    }

    /** An attempt that was [saved] finishes the run; one that wasn't is kept for Retry or Discard. */
    private fun settle(
        attempt: Attempt,
        saved: Boolean,
    ) {
        unsaved = if (saved) null else attempt
        // A save that failed may still have reached the store: read the history again on the next start.
        if (!saved) hintCache = null
        if (saved && spot != null) return loopFinished(attempt)
        mutableState.update {
            if (saved) {
                it.copy(saveFailed = false, finishedAttemptId = attempt.id, finishedBand = attempt.band)
            } else {
                it.copy(saveFailed = true)
            }
        }
    }

    /** A saved spot loop: counts it towards the clean streak and starts the next loop, unless the target is reached. */
    private fun loopFinished(attempt: Attempt) {
        val clean = attempt.band == Band.CLEAN
        var reached = false
        mutableState.update {
            val loop = checkNotNull(it.spotLoop)
            val streak = if (clean) loop.cleanLoops + 1 else 0
            reached = streak >= Tunables.SPOT_LOOP_CLEAN_TARGET
            val result = LoopResult(clean, attempt.wrongNotes, attempt.incompleteChords)
            it.copy(
                saveFailed = false,
                spotLoop = loop.copy(cleanLoops = streak, lastLoop = result, offerFull = reached),
            )
        }
        if (!reached) restart()
    }

    /** After the "bars are clean" card: loops on, with the clean streak starting again. */
    public fun keepLooping() {
        val loop = mutableState.value.spotLoop ?: return
        mutableState.update { it.copy(spotLoop = loop.restarted()) }
        restart()
    }

    /** The first key of a run ends any passage playing for the learner and hides the Listen button. */
    private fun keyPressedInRun() {
        demo?.cancel()
        if (!mutableState.value.played) mutableState.update { it.copy(played = true) }
    }

    /**
     * A key press: lights it, flashes it and, in wait mode, [needed] (the notes the matcher still needs of the step,
     * [advanced] when the step just moved on) points a wrong key to the right one.
     */
    private fun pressKey(
        note: MidiNote,
        flash: KeyFlash,
        needed: Set<MidiNote>? = null,
        advanced: Boolean = false,
    ) {
        if (!mutableState.value.showsVerdicts) {
            // The key still lights, in a neutral colour: no verdict, no flash, no last-key text.
            return mutableState.update { it.copy(keysDown = it.keysDown + note) }
        }
        mutableState.update {
            it
                .copy(
                    keysDown = it.keysDown + note,
                    wrongKeys = if (flash.correct) it.wrongKeys - note else it.wrongKeys + note,
                    flash = flash,
                    lastKey = flash,
                ).withHint(note, flash.correct, needed, advanced)
                .let { state -> if (noHints) state.copy(hintGlow = emptySet()) else state }
        }
        flashTimer?.cancel()
        flashTimer =
            viewModelScope.launch {
                delay(Tunables.KEY_FLASH_MS)
                mutableState.update { it.copy(flash = null) }
            }
    }

    private fun updateKeys(event: MidiEvent) {
        if (event is NoteOff) mutableState.update { it.released(event.note) }
        if (event is ControlChange && event.releasesAllKeys()) mutableState.update { it.releasedAll() }
    }

    /** One wait-mode run's progress: the matcher state, its events so far and the wall time of the first note. */
    private inner class WaitRun {
        var match: MatchState = WaitModeMatcher.start(passage)
        val events = mutableListOf<MatchEvent>()
        val midi = mutableListOf<MidiEvent>()
        var startedAt: Instant? = null

        /** Where notes still sounding end: the cap, or the moment the tail ended otherwise. */
        private var endNanos = 0L

        suspend fun play() {
            startHintStep()
            coroutineScope {
                val ended = CompletableDeferred<Unit>()
                launch {
                    try {
                        input.events(device).collect { onEvent(it, this@coroutineScope, ended) }
                    } catch (_: MidiDeviceUnavailableException) {
                        // Not listed (or gone) when the run opened it: same as a drop.
                    }
                    end(ended) // a drop: after the completing note it grades the run, before it nothing is saved
                }
                ended.await()
                coroutineContext.cancelChildren() // the reader (closing the piano) and a cap timer still running
            }
            if (!match.complete) return mutableState.update { it.disconnected() }
            save(
                Grader.attempt(
                    id = newId(),
                    passage = passage,
                    deviceId = device.id,
                    startedAt = checkNotNull(startedAt),
                    startedAtNanos = checkNotNull(match.startedAtNanos),
                    events = events,
                    playedNotes = PlayedNotes.from(midi, endNanos),
                    strictness = strictness,
                ),
            )
        }

        private fun end(
            ended: CompletableDeferred<Unit>,
            at: Long = time.nowNanos(),
        ) {
            if (ended.complete(Unit)) endNanos = at
        }

        private fun onEvent(
            event: MidiEvent,
            scope: CoroutineScope,
            ended: CompletableDeferred<Unit>,
        ) {
            // Events already buffered when the tail ended are ignored.
            when {
                ended.isCompleted -> {}

                event is LinkResumed -> {
                    onLinkResumed(event, ended)
                }

                match.complete -> {
                    onTail(event, ended)
                }

                else -> {
                    midi += event
                    if (event is NoteOn) {
                        keyPressedInRun()
                        onNoteOn(event)
                        if (match.complete) startTail(scope, ended)
                    } else {
                        updateKeys(event)
                    }
                }
            }
        }

        /**
         * The link dropped and came back: wait mode loses nothing that matters (it waits for the next right key), but
         * a key let go in the gap would stay lit and held notes would never end. Releases them all at the resume
         * stamp; a take that was only waiting for its keys to come up ends here.
         */
        private fun onLinkResumed(
            event: LinkResumed,
            ended: CompletableDeferred<Unit>,
        ) {
            PlayedNotes.sounding(midi).forEach { midi += NoteOff(event.timestampNanos, 0, it, 0) }
            mutableState.update { it.releasedAll() }
            if (match.complete) end(ended)
        }

        /** After the completing NoteOn: keys up and pedal changes join the take until nothing sounds. */
        private fun onTail(
            event: MidiEvent,
            ended: CompletableDeferred<Unit>,
        ) {
            if (event is NoteOn) return end(ended, event.timestampNanos)
            midi += event
            updateKeys(event)
            if (PlayedNotes.sounding(midi).isEmpty()) end(ended)
        }

        /** Ends the tail at the cap, counted from the completing NoteOn, whatever still sounds. */
        private fun startTail(
            scope: CoroutineScope,
            ended: CompletableDeferred<Unit>,
        ) {
            val capNanos =
                midi.last().timestampNanos + Tunables.WAIT_MODE_RELEASE_TIMEOUT_MS.milliseconds.inWholeNanoseconds
            scope.launch {
                delay((capNanos - time.nowNanos()).coerceAtLeast(0).nanoseconds)
                end(ended, capNanos)
            }
        }

        private fun onNoteOn(event: NoteOn) {
            if (startedAt == null) startedAt = clock.now()
            val stepBefore = match.stepIndex
            val step = WaitModeMatcher.reduce(match, event)
            match = step.state
            if (match.complete) {
                hintTimer?.cancel() // no step left to help with, and a late hint must not count as shown
            } else if (match.stepIndex != stepBefore) {
                startHintStep()
            }
            events += step.events
            mutableState.update {
                it.copy(steps = statuses(it.steps, match), currentStep = match.stepIndex)
            }
            val advanced = match.complete || match.stepIndex != stepBefore
            val needed = if (match.complete) emptySet() else passage.steps[match.stepIndex].pitches - match.pressed
            pressKey(event.note, KeyFlash(event.note, correct = step.events.none { it is WrongNote }), needed, advanced)
        }
    }

    /** A click pattern as handed to the metronome. */
    private class ClickPattern(
        val firstBeatNanos: Long,
        val beatNanos: Long,
        val beatsPerBar: Int,
    ) {
        suspend fun start(metronome: MetronomePort) = metronome.start(firstBeatNanos, beatNanos, beatsPerBar)
    }

    /** How a timed run ended. */
    private sealed interface RunOutcome {
        /** The clock ran to the judge's end: the take is graded and saved. */
        data object Completed : RunOutcome

        /** The piano's stream ended or failed first. */
        data object Dropped : RunOutcome

        /** A wrong or missed note stopped the run; the take is discarded. */
        data class Stopped(
            val reason: RestartReason,
        ) : RunOutcome
    }

    /**
     * One timed run on the rung at [bpm]: arms itself (waits `Tunables.TEMPO_AUTO_START_DELAY_MS` with the piano open),
     * counts in and runs the clock until the judge's end or the first wrong or missed note. Keys before the judge's
     * first window (arming, count-in) light neutrally and are not part of the take.
     */
    private inner class TimedRun(
        private val bpm: Int,
        private val latencyOffsetNanos: Long,
    ) {
        var startedAt: Instant? = null

        /** True once the run has started over, stopped or its clock has ended: later events belong to someone else. */
        private var finished = false
        private val ended = CompletableDeferred<RunOutcome>()
        private var judge: TempoJudge? = null
        private var startNanos = 0L
        private var firstBeatNanos = 0L
        private var endNanos = 0L

        /** The cursor step whose hint timer runs (or ran), -1 before the run plays. */
        private var hintStep = -1

        /** Steps that got a Correct or TimingOff verdict, possibly before the cursor reached them. */
        private val answered = mutableSetOf<Int>()

        /** One click: the time signature's beat unit (a quarter note is [beatNanos] of the tempo). */
        private val beatNanos = beatNanos(bpm) * QUARTER_UNIT / passage.timeSignature.beatUnit
        private val beatsPerBar = passage.timeSignature.beats
        private val midi = mutableListOf<MidiEvent>()

        /** The take's played notes, rebased so 0 is the written time of tick 0; keys still down end with the run. */
        fun notes() = PlayedNotes.from(midi, endNanos).map { it.shiftedBy(-startNanos) }

        /** Runs until the judge's end, a mistake or the end of the piano's stream. */
        suspend fun play(): RunOutcome =
            coroutineScope {
                launch {
                    try {
                        input.events(device).collect { onEvent(it) }
                    } catch (_: MidiDeviceUnavailableException) {
                        // Not listed (or gone) when the run opened it: same as a drop.
                    }
                    ended.complete(RunOutcome.Dropped)
                }
                val scope = this
                launch {
                    delay(Tunables.TEMPO_AUTO_START_DELAY_MS)
                    begin(scope)
                }
                val outcome = ended.await()
                coroutineContext.cancelChildren() // the reader (closing the piano), the arming delay, a clock
                outcome
            }

        private fun onEvent(event: MidiEvent) {
            // A run that started over, or whose take is over, reads nothing more.
            if (finished) return
            if (event is NoteOn) keyPressedInRun()
            if (event is NoteOn && mutableState.value.linkResumed) mutableState.update { it.copy(linkResumed = false) }
            updateKeys(event)
            val judge = judge
            when {
                event is LinkResumed -> {
                    onLinkResumed(judge)
                }

                // Arming or counting in: the key sounds and lights neutrally, but nothing judges it.
                event is NoteOn && (judge == null || judge.beforeFirstWindow(event.timestampNanos)) -> {
                    mutableState.update { it.copy(keysDown = it.keysDown + event.note) }
                }

                judge == null -> {
                    if (event !is NoteOff) midi += event // a pedal already down is part of the take
                }

                else -> {
                    midi += event
                    if (event is NoteOn) onNoteOn(event, judge)
                }
            }
        }

        /**
         * The link dropped and came back: messages in the gap are lost. While arming there is no take, so only the keys
         * come up. Otherwise the take is meaningless: it is discarded unsaved and the run starts over like [restart]
         * (same rung, mode and spot loop), saying why until the next note.
         */
        private fun onLinkResumed(judge: TempoJudge?) {
            if (judge == null) {
                mutableState.update { it.releasedAll() }
            } else {
                finished = true // this reader may still hold buffered events: the new run owns the piano's notes now
                restart()
                mutableState.update { it.copy(linkResumed = true) }
            }
        }

        private suspend fun begin(scope: CoroutineScope) {
            startedAt = clock.now()
            firstBeatNanos = time.nowNanos() + Tunables.TEMPO_COUNT_IN_LEAD_MS.milliseconds.inWholeNanoseconds
            startNanos = firstBeatNanos + beatsPerBar * beatNanos
            val timing = TempoJudge(passage, bpm, startNanos, latencyOffsetNanos, strictness)
            judge = timing
            endNanos = timing.endNanos + latencyOffsetNanos.coerceAtLeast(0)
            val click = ClickPattern(firstBeatNanos, beatNanos, beatsPerBar)
            pattern = click
            if (mutableState.value.clickOn) click.start(metronome)
            scope.launch {
                runClock(timing)
                if (!finished) {
                    finished = true
                    ended.complete(RunOutcome.Completed)
                }
            }
        }

        /** A wrong or missed [note] stops the run: nothing more is read and [play] returns. */
        private fun stop(
            kind: RestartKind,
            note: MidiNote,
        ) {
            finished = true
            ended.complete(RunOutcome.Stopped(RestartReason(kind, note)))
        }

        private fun onNoteOn(
            event: NoteOn,
            judge: TempoJudge,
        ) {
            val flash =
                when (val verdict = judge.onNoteOn(event)) {
                    is LiveVerdict.Correct -> {
                        mark(verdict.at, LiveMark.Correct)
                        KeyFlash(event.note, correct = true)
                    }

                    is LiveVerdict.TimingOff -> {
                        mark(verdict.at, LiveMark.TimingOff(verdict.deviationMs))
                        KeyFlash(event.note, correct = true, deviationMs = verdict.deviationMs)
                    }

                    is LiveVerdict.Wrong -> {
                        KeyFlash(event.note, correct = false)
                    }
                }
            publish(time.nowNanos(), judge)
            pressKey(event.note, flash)
            if (!flash.correct) stop(RestartKind.WRONG, event.note)
        }

        private fun mark(
            at: ScoreNoteRef,
            mark: LiveMark,
        ) {
            answered += at.stepIndex
            if (at.stepIndex == hintStep) hintTimer?.cancel() // played in time: no help needed for this step
            if (!mutableState.value.showsVerdicts) return // no live verdicts in a perform take
            mutableState.update { it.copy(liveMarks = it.liveMarks + (at to mark)) }
        }

        /** Publishes the clock's state at every beat subdivision until the judge's end. */
        private suspend fun runClock(judge: TempoJudge) {
            val subdivision = beatNanos / Tunables.TEMPO_CURSOR_SUBDIVISIONS
            while (true) {
                val now = time.nowNanos()
                publish(now, judge)
                val missed = judge.missedNote(now)
                if (missed != null) return stop(RestartKind.MISSED, missed.pitch)
                if (now >= endNanos) return
                val next = firstBeatNanos + ((now - firstBeatNanos).floorDiv(subdivision) + 1) * subdivision
                delay((next - now).nanoseconds)
            }
        }

        private fun publish(
            now: Long,
            judge: TempoJudge,
        ) {
            val sinceFirstBeat = now - firstBeatNanos
            val beat = if (sinceFirstBeat < 0) 0 else (sinceFirstBeat / beatNanos % beatsPerBar).toInt() + 1
            val playing = now >= startNanos
            val cursor = if (playing) judge.cursorStep(now) else 0
            if (playing && cursor != hintStep) {
                hintStep = cursor
                startHintStep(answered = cursor in answered)
            }
            mutableState.update {
                val marked = it.showsVerdicts
                it.copy(
                    phase = if (playing) RunPhase.PLAYING else RunPhase.COUNT_IN,
                    beat = beat,
                    playNow = playing && now < startNanos + beatNanos,
                    currentStep = cursor,
                    steps = it.steps.mapIndexed { i, step -> step.copy(status = cursorStatus(i, cursor, marked)) },
                )
            }
        }
    }
}

private const val UNIT_ZERO_ID = "unit-0"

/** Whether every skill of [passage] belongs to Unit 0 (a passage with no skills is not): the staff is left out. */
private fun isUnitZero(passage: Passage): Boolean {
    val unit = Curriculum.units.firstOrNull { it.id == UNIT_ZERO_ID } ?: return false
    return passage.skills.isNotEmpty() && unit.skills.containsAll(passage.skills)
}

/** The loop with its clean streak and the "bars are clean" offer cleared. */
private fun SpotLoopState.restarted() = copy(cleanLoops = 0, offerFull = false)

/** The beat unit of a quarter note in a time signature (the 4 of 3/4). */
private const val QUARTER_UNIT = 4

/** A timed step's colour: the cursor step waits (amber) in practice; a perform take draws every note neutrally. */
private fun cursorStatus(
    step: Int,
    cursor: Int,
    showsVerdicts: Boolean,
) = if (step == cursor && showsVerdicts) StepStatus.WAITING else StepStatus.UPCOMING

private fun statuses(
    steps: List<StepView>,
    match: MatchState,
): List<StepView> =
    steps.mapIndexed { i, step ->
        val status =
            when {
                match.complete || i < match.stepIndex -> StepStatus.DONE
                i == match.stepIndex -> StepStatus.WAITING
                else -> StepStatus.UPCOMING
            }
        step.copy(status = status)
    }

/**
 * After a press of [note] in wait mode, with [needed] the notes the matcher still needs of the step ([advanced] when
 * the step just moved on, which drops any hint). A wrong key points to the nearest needed key and makes the needed
 * keys glow. A right key in the same step recomputes both from what is still needed (the matcher forgets chord
 * presses after the chord window), and the hint goes when nothing is.
 */
private fun PracticeState.withHint(
    note: MidiNote,
    correct: Boolean,
    needed: Set<MidiNote>?,
    advanced: Boolean,
): PracticeState =
    when {
        needed == null -> this
        advanced -> copy(hint = null, hintGlow = emptySet())
        !correct -> pointedFrom(note.number, needed)
        hint == null -> this
        else -> pointedFrom(hint.played, needed)
    }

/** The state with a hint from the [played] key to the nearest of [needed], which glow; none if nothing is needed. */
private fun PracticeState.pointedFrom(
    played: Int,
    needed: Set<MidiNote>,
): PracticeState {
    val expected = needed.filter { it.number != played }.minByOrNull { abs(it.number - played) }
    return if (expected == null) {
        copy(hint = null, hintGlow = emptySet())
    } else {
        copy(hint = wrongKeyHint(played, expected.number), hintGlow = needed)
    }
}

private fun PracticeState.released(note: MidiNote) = copy(keysDown = keysDown - note, wrongKeys = wrongKeys - note)

/** All Notes Off / All Sound Off: every key comes up, pedal or not; a running flash fades on its own timer. */
private fun PracticeState.releasedAll() = copy(keysDown = emptySet(), wrongKeys = emptySet())

/** The run is discarded: the piano is gone, nothing is on screen from it. */
private fun PracticeState.disconnected() =
    copy(
        connection = PracticeConnection.DISCONNECTED,
        keysDown = emptySet(),
        wrongKeys = emptySet(),
        phase = RunPhase.READY,
        beat = 0,
        liveMarks = emptyMap(),
    )
