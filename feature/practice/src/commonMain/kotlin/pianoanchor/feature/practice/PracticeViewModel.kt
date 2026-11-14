package pianoanchor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Passage
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.WrongNote
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.grading.Grader
import pianoanchor.engine.matcher.MatchState
import pianoanchor.engine.matcher.WaitModeMatcher
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Wait-mode practice of [passage] on [device]: follows the piano's notes with [WaitModeMatcher], shows progress and
 * the live keys, and when the passage is done grades the run ([Grader]), saves it to [attempts] and sets
 * [PracticeState.finishedAttemptId]. A run that ends early (disconnect, [restart], leaving the screen) is discarded.
 * When the store is unavailable the graded attempt is kept with [PracticeState.saveFailed] until [retrySave] or
 * [discardRun]. [clock] gives the attempt's start time (at the first note); [newId] the attempt's id.
 */
public class PracticeViewModel(
    private val device: MidiDevice,
    private val passage: Passage,
    private val input: MidiInputPort,
    private val attempts: AttemptRepositoryPort,
    private val clock: Clock,
    private val newId: () -> String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PracticeState.start(passage))
    public val state: StateFlow<PracticeState> = mutableState.asStateFlow()

    private var run: Job? = null
    private var flashTimer: Job? = null

    /** The graded attempt whose save failed, until it is saved or discarded; null while a retry runs. */
    private var unsaved: Attempt? = null

    init {
        start()
    }

    /** Discards the run and starts over, reopening the piano once the old run has closed it (one reader per input). */
    public fun restart() {
        flashTimer?.cancel()
        unsaved = null
        mutableState.value = PracticeState.start(passage)
        start()
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
                    return@launch keepUnsaved(attempt)
                }
                finish(attempt)
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

    private fun start() {
        val previous = run
        run =
            viewModelScope.launch {
                previous?.cancelAndJoin()
                play()
            }
    }

    private suspend fun play() {
        val run = Run()
        try {
            input
                .events(device)
                .transformWhile<MidiEvent, Unit> { event ->
                    run.onEvent(event)
                    !run.match.complete
                }.collect()
        } catch (_: MidiDeviceUnavailableException) {
            // Not listed (or gone) when the run opened it: same as a drop.
        }
        if (!run.match.complete) {
            return mutableState.update {
                it.copy(connection = PracticeConnection.DISCONNECTED, keysDown = emptySet(), wrongKeys = emptySet())
            }
        }
        val attempt =
            Grader.attempt(
                id = newId(),
                passage = passage,
                deviceId = device.id,
                startedAt = checkNotNull(run.startedAt),
                startedAtNanos = checkNotNull(run.match.startedAtNanos),
                events = run.events,
            )
        try {
            attempts.save(attempt)
        } catch (_: StorageUnavailableException) {
            return keepUnsaved(attempt)
        }
        finish(attempt)
    }

    private fun keepUnsaved(attempt: Attempt) {
        unsaved = attempt
        mutableState.update { it.copy(saveFailed = true) }
    }

    private fun finish(attempt: Attempt) {
        mutableState.update { it.copy(saveFailed = false, finishedAttemptId = attempt.id) }
    }

    /** One run's progress: the matcher state, its events so far and the wall time of the first note. */
    private inner class Run {
        var match: MatchState = WaitModeMatcher.start(passage)
        val events = mutableListOf<MatchEvent>()
        var startedAt: Instant? = null

        fun onEvent(event: MidiEvent) {
            if (event is NoteOn) return onNoteOn(event)
            if (event !is NoteOff) return
            mutableState.update { it.copy(keysDown = it.keysDown - event.note, wrongKeys = it.wrongKeys - event.note) }
        }

        private fun onNoteOn(event: NoteOn) {
            if (startedAt == null) startedAt = clock.now()
            val step = WaitModeMatcher.reduce(match, event)
            match = step.state
            events += step.events
            val flash = KeyFlash(event.note, correct = step.events.none { it is WrongNote })
            mutableState.update {
                it.copy(
                    steps = statuses(it.steps, match),
                    currentStep = match.stepIndex,
                    keysDown = it.keysDown + event.note,
                    wrongKeys = if (flash.correct) it.wrongKeys - event.note else it.wrongKeys + event.note,
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
    }
}

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
