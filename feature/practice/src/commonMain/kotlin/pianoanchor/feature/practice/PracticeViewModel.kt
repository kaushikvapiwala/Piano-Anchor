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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.WrongNote
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MetronomePort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.analysis.PlayedNotes
import pianoanchor.engine.grading.Grader
import pianoanchor.engine.matcher.LiveVerdict
import pianoanchor.engine.matcher.MatchState
import pianoanchor.engine.matcher.TempoJudge
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.matcher.WaitModeMatcher
import pianoanchor.engine.matcher.beatNanos
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/**
 * Practice of [passage] on [device]. Wait mode follows the piano's notes with [WaitModeMatcher]; a timed rung
 * ([selectRung]) waits for a key, counts in one bar with [metronome], then moves the cursor with [time] and
 * judges each note live ([TempoJudge]). It shows progress and the live keys, and when the run is done grades it
 * ([Grader]), saves it to [attempts] with its played notes and sets [PracticeState.finishedAttemptId]. A run that
 * ends early (disconnect, [restart], [selectRung], leaving the screen) is discarded. When the store is unavailable
 * the graded attempt is kept with [PracticeState.saveFailed] until [retrySave] or [discardRun]. [clock] gives the
 * attempt's start time (at the first note); [newId] the attempt's id; [clickSetting] keeps the metronome mute for
 * the app session.
 */
public class PracticeViewModel(
    private val device: MidiDevice,
    private val passage: Passage,
    private val input: MidiInputPort,
    private val attempts: AttemptRepositoryPort,
    private val metronome: MetronomePort,
    private val time: ClockPort,
    private val clock: Clock,
    private val clickSetting: ClickSetting = ClickSetting(),
    private val newId: () -> String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PracticeState.start(passage).copy(clickOn = clickSetting.on.value))
    public val state: StateFlow<PracticeState> = mutableState.asStateFlow()

    private var run: Job? = null
    private var flashTimer: Job? = null

    /** The graded attempt whose save failed, until it is saved or discarded; null while a retry runs. */
    private var unsaved: Attempt? = null

    /** The click pattern of the timed run in progress, null outside one. */
    private var pattern: ClickPattern? = null

    init {
        viewModelScope.launch { refreshDoneRungs() }
        selectRung(TempoRung.WAIT)
    }

    /** Discards the run and starts over on the same rung, reopening the piano once the old run has closed it. */
    public fun restart() {
        selectRung(mutableState.value.rung)
    }

    /**
     * Discards any run and goes to [rung], ready to start: wait mode as before, a timed rung on the first key. The
     * piano is reopened once the old run has closed it (one reader per input).
     */
    public fun selectRung(rung: TempoRung) {
        flashTimer?.cancel()
        unsaved = null
        mutableState.update {
            PracticeState.start(passage).copy(rung = rung, doneRungs = it.doneRungs, clickOn = it.clickOn)
        }
        val previous = run
        run =
            viewModelScope.launch {
                previous?.cancelAndJoin()
                val bpm = rung.bpm(passage)
                if (bpm == null) WaitRun().play() else playTimed(bpm)
            }
    }

    /**
     * Switches the metronome on or off, also while a run is in progress: the pattern keeps its timing, so turning the
     * click back on rejoins the beat. The choice is kept for the app session.
     */
    public fun toggleClick() {
        val on = !clickSetting.on.value
        clickSetting.on.value = on
        mutableState.update { it.copy(clickOn = on) }
        val running = pattern ?: return
        viewModelScope.launch {
            if (on) running.start(metronome) else metronome.stop()
            // The run ended (and stopped the click) while this start ran: don't leave it clicking.
            if (on && pattern !== running) metronome.stop()
        }
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
                refreshDoneRungs()
                settle(attempt, saved = true)
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
        run?.cancelAndJoin()
    }

    private suspend fun playTimed(bpm: Int) {
        val timed = TimedRun(bpm)
        val finished =
            try {
                timed.play()
            } finally {
                pattern = null
                withContext(NonCancellable) { metronome.stop() }
            }
        if (!finished) return mutableState.update { it.disconnected() }
        mutableState.update { it.copy(phase = RunPhase.FINISHED, beat = 0) }
        save(
            Grader.timedAttempt(
                id = newId(),
                passage = passage,
                deviceId = device.id,
                startedAt = checkNotNull(timed.startedAt),
                bpm = bpm,
                notes = timed.notes(),
            ),
        )
    }

    private suspend fun save(attempt: Attempt) {
        try {
            attempts.save(attempt)
        } catch (_: StorageUnavailableException) {
            return settle(attempt, saved = false)
        }
        refreshDoneRungs()
        settle(attempt, saved = true)
    }

    /** Marks the rungs whose tempo has a clean timed attempt; a store that can't be read leaves them as they are. */
    private suspend fun refreshDoneRungs() {
        val tempos =
            try {
                attempts.cleanTempos(passage.id)
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
        mutableState.update {
            if (saved) it.copy(saveFailed = false, finishedAttemptId = attempt.id) else it.copy(saveFailed = true)
        }
    }

    private fun pressKey(
        note: MidiNote,
        flash: KeyFlash,
    ) {
        mutableState.update {
            it.copy(
                keysDown = it.keysDown + note,
                wrongKeys = if (flash.correct) it.wrongKeys - note else it.wrongKeys + note,
                flash = flash,
                lastKey = flash,
            )
        }
        flashTimer?.cancel()
        flashTimer =
            viewModelScope.launch {
                delay(Tunables.KEY_FLASH_MS)
                mutableState.update { it.copy(flash = null) }
            }
    }

    /** One wait-mode run's progress: the matcher state, its events so far and the wall time of the first note. */
    private inner class WaitRun {
        var match: MatchState = WaitModeMatcher.start(passage)
        val events = mutableListOf<MatchEvent>()
        val midi = mutableListOf<MidiEvent>()
        var startedAt: Instant? = null

        suspend fun play() {
            try {
                input
                    .events(device)
                    .transformWhile<MidiEvent, Unit> { event ->
                        onEvent(event)
                        !match.complete
                    }.collect()
            } catch (_: MidiDeviceUnavailableException) {
                // Not listed (or gone) when the run opened it: same as a drop.
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
                    playedNotes = PlayedNotes.from(midi),
                ),
            )
        }

        fun onEvent(event: MidiEvent) {
            midi += event
            if (event is NoteOn) return onNoteOn(event)
            if (event is NoteOff) mutableState.update { it.released(event.note) }
        }

        private fun onNoteOn(event: NoteOn) {
            if (startedAt == null) startedAt = clock.now()
            val step = WaitModeMatcher.reduce(match, event)
            match = step.state
            events += step.events
            mutableState.update {
                it.copy(steps = statuses(it.steps, match), currentStep = match.stepIndex)
            }
            pressKey(event.note, KeyFlash(event.note, correct = step.events.none { it is WrongNote }))
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

    /**
     * One timed run on the rung at [bpm]: waits for the starting key, then counts in and runs the clock until the
     * judge's end. Notes after the starting key are the take.
     */
    private inner class TimedRun(
        private val bpm: Int,
    ) {
        var startedAt: Instant? = null
        private var judge: TempoJudge? = null
        private var startNanos = 0L
        private var firstBeatNanos = 0L
        private var endNanos = 0L

        /** One click: the time signature's beat unit (a quarter note is [beatNanos] of the tempo). */
        private val beatNanos = beatNanos(bpm) * QUARTER_UNIT / passage.timeSignature.beatUnit
        private val beatsPerBar = passage.timeSignature.beats
        private val midi = mutableListOf<MidiEvent>()

        /** The take's played notes, rebased so 0 is the written time of tick 0; keys still down end with the run. */
        fun notes() = PlayedNotes.from(midi, endNanos).map { it.shiftedBy(-startNanos) }

        /** Runs until the judge's end (true) or until the piano's stream ends first (false). */
        suspend fun play(): Boolean =
            coroutineScope {
                val ended = CompletableDeferred<Boolean>()
                launch {
                    try {
                        input.events(device).collect { onEvent(it, this@coroutineScope, ended) }
                    } catch (_: MidiDeviceUnavailableException) {
                        // Not listed (or gone) when the run opened it: same as a drop.
                    }
                    ended.complete(false)
                }
                val finished = ended.await()
                coroutineContext.cancelChildren() // the reader (closing the piano) and a clock still running
                finished
            }

        private suspend fun onEvent(
            event: MidiEvent,
            scope: CoroutineScope,
            ended: CompletableDeferred<Boolean>,
        ) {
            if (event is NoteOff) mutableState.update { it.released(event.note) }
            val judge = judge
            if (judge == null) {
                // The key that starts the count-in is not graded and not part of the take; a pedal already down is.
                if (event is NoteOn) {
                    begin(event, scope, ended)
                } else if (event !is NoteOff) {
                    midi += event
                }
                return
            }
            midi += event
            if (event is NoteOn) onNoteOn(event, judge)
        }

        private suspend fun begin(
            starter: NoteOn,
            scope: CoroutineScope,
            ended: CompletableDeferred<Boolean>,
        ) {
            startedAt = clock.now()
            firstBeatNanos = time.nowNanos() + Tunables.TEMPO_COUNT_IN_LEAD_MS.milliseconds.inWholeNanoseconds
            startNanos = firstBeatNanos + beatsPerBar * beatNanos
            val timing = TempoJudge(passage, bpm, startNanos)
            judge = timing
            endNanos = timing.endNanos
            mutableState.update { it.copy(keysDown = it.keysDown + starter.note) }
            val click = ClickPattern(firstBeatNanos, beatNanos, beatsPerBar)
            pattern = click
            if (clickSetting.on.value) click.start(metronome)
            scope.launch {
                runClock(timing)
                ended.complete(true)
            }
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
        }

        private fun mark(
            at: ScoreNoteRef,
            mark: LiveMark,
        ) = mutableState.update { it.copy(liveMarks = it.liveMarks + (at to mark)) }

        /** Publishes the clock's state at every beat subdivision until the judge's end. */
        private suspend fun runClock(judge: TempoJudge) {
            val subdivision = beatNanos / Tunables.TEMPO_CURSOR_SUBDIVISIONS
            while (true) {
                val now = time.nowNanos()
                publish(now, judge)
                if (now >= judge.endNanos) return
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
            mutableState.update {
                it.copy(
                    phase = if (playing) RunPhase.PLAYING else RunPhase.COUNT_IN,
                    beat = beat,
                    currentStep = cursor,
                    steps = it.steps.mapIndexed { i, step -> step.copy(status = cursorStatus(i, cursor)) },
                )
            }
        }
    }
}

/** The beat unit of a quarter note in a time signature (the 4 of 3/4). */
private const val QUARTER_UNIT = 4

private fun cursorStatus(
    step: Int,
    cursor: Int,
) = if (step == cursor) StepStatus.WAITING else StepStatus.UPCOMING

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

private fun PracticeState.released(note: MidiNote) = copy(keysDown = keysDown - note, wrongKeys = wrongKeys - note)

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
