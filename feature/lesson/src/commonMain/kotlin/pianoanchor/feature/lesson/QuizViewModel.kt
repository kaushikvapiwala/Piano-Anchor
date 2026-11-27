package pianoanchor.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Rating
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MetronomePort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.learner.learnerDay
import pianoanchor.engine.lesson.NoteLabelsUseCase
import pianoanchor.engine.lesson.Quiz
import pianoanchor.engine.lesson.QuizMaker
import pianoanchor.engine.lesson.TryRun
import pianoanchor.engine.midi.PedalTapDetector
import kotlin.time.Clock

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * The review quizzes of Today's warm-up (`docs/algorithms/quizzes.md`): runs [quizzes] in order, each a [TryRun] of its
 * check on the piano read from [input] on [device], opened once for the screen. A quiz with a click starts [metronome]
 * with a one-bar count-in ([time] stamps its start); a passed one shows its tick for `LESSON_TRY_DONE_PAUSE_MS` and
 * moves on. Each finished or skipped quiz is rated by [QuizMaker.rating] and recorded with [learner] on the learner day
 * of [clock] in [zone] (id from [newId]); a skill that already has a review that day records nothing. Leaving mid-way
 * keeps what was recorded.
 *
 * With `Settings.pedalControlOn` in [settings], a pedal tap during the summary asks for Done on [pedalDone]; during a
 * quiz the pedal is ignored as a control.
 *
 * An input has one reader at a time: [close] returns once the piano is closed and the click has stopped, before
 * another screen opens the piano.
 */
@Suppress("LongParameterList", "TooManyFunctions") // Ports to wire; one small function per step.
public class QuizViewModel(
    private val device: MidiDevice,
    private val quizzes: List<Quiz>,
    private val input: MidiInputPort,
    private val metronome: MetronomePort,
    private val time: ClockPort,
    private val learner: LearnerModel,
    private val noteLabels: NoteLabelsUseCase,
    private val settings: SettingsRepositoryPort,
    private val clock: Clock,
    private val zone: TimeZone,
    private val newId: () -> String,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            QuizState(
                1,
                quizzes.size,
                requireNotNull(quizzes.firstOrNull()) { "a quiz run needs at least one quiz" },
                null,
                emptySet(),
                null,
                LessonConnection.CONNECTED,
                null,
                false,
            ),
        )
    public val state: StateFlow<QuizState> = mutableState.asStateFlow()

    private val doneRequests = Channel<Unit>(Channel.BUFFERED)

    /** Emits once when a pedal tap asks for Done; the host closes the quizzes and leaves, as for the button. */
    public val pedalDone: Flow<Unit> = doneRequests.receiveAsFlow()
    private val pedal = PedalTapDetector()
    private var doneAsked = false

    private var reader: Job? = null

    /** The current quiz: waits for the learner's notes. */
    private var work: Job? = null

    /** Records a finished or skipped quiz, shows the tick and moves on. */
    private var finishing: Job? = null

    /** True from the moment a quiz is passed or skipped until the next one starts. */
    private var settling = false
    private var run: TryRun? = null

    /** False during a count-in: notes are not counted. */
    private var accepting = false
    private var index = 0
    private var clean = 0
    private var closed = false

    /** Letters the strip's keys as the learner's setting and progress say, once they are read. */
    private suspend fun loadNoteLabels() {
        val labels = noteLabels.load()
        mutableState.update { it.copy(noteLabels = labels) }
    }

    init {
        reader = viewModelScope.launch { readPiano() }
        viewModelScope.launch { watchPedalSetting() }
        viewModelScope.launch { loadNoteLabels() }
        startQuiz()
    }

    /** Gives up the current quiz: it is rated AGAIN and the next one starts. Nothing happens during the summary. */
    public fun skip() {
        val current = mutableState.value
        if (closed || current.summary != null || settling) return
        settle(skipped = true)
    }

    /** Stops the quizzes and returns once the piano is closed and the click has stopped; later taps start nothing. */
    public suspend fun close() {
        closed = true
        withContext(NonCancellable) {
            finishing?.cancelAndJoin()
            work?.cancelAndJoin()
            reader?.cancelAndJoin()
        }
    }

    private fun startQuiz() {
        if (closed) return
        val quiz = quizzes[index]
        val previous = work
        run = TryRun(quiz.check)
        accepting = false
        mutableState.update {
            it.copy(number = index + 1, quiz = quiz, progress = run?.progress, litKeys = emptySet(), countIn = null)
        }
        work =
            viewModelScope.launch {
                // The previous quiz releases the click first; this wait can't be cancelled.
                withContext(NonCancellable) { previous?.cancelAndJoin() }
                val bpm = clickOf(quiz.check)
                try {
                    if (bpm == null) {
                        accepting = true
                    } else {
                        countIn(metronome, time, bpm, { n -> mutableState.update { it.copy(countIn = n) } }) {
                            accepting = true
                        }
                    }
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { if (bpm != null) metronome.stop() }
                }
            }
    }

    private fun settle(skipped: Boolean) {
        settling = true
        // The quiz is over: stop its count-in and click now, not when the next quiz starts.
        accepting = false
        work?.cancel()
        finishing = viewModelScope.launch { finish(skipped) }
    }

    /** Rates and records the current quiz; a passed one shows its tick first. Then the next quiz, or the summary. */
    private suspend fun finish(skipped: Boolean) {
        val quiz = quizzes[index]
        val rating = QuizMaker.rating(run?.progress?.wrongCount ?: 0, skipped)
        if (rating == Rating.GOOD) clean++
        withContext(NonCancellable) { record(quiz, rating) }
        if (!skipped) delay(Tunables.LESSON_TRY_DONE_PAUSE_MS)
        index++
        settling = false
        if (index < quizzes.size) {
            startQuiz()
        } else {
            run = null
            accepting = false
            work?.cancel()
            mutableState.update {
                it.copy(
                    quiz = null,
                    progress = null,
                    litKeys = emptySet(),
                    countIn = null,
                    summary = QuizSummary(quizzes.size, clean),
                )
            }
        }
    }

    private suspend fun record(
        quiz: Quiz,
        rating: Rating,
    ) {
        try {
            learner.recordQuiz(quiz.skillId, rating, learnerDay(clock.now(), zone), newId())
        } catch (_: StorageUnavailableException) {
            mutableState.update { it.copy(storageFailed = true) }
        }
    }

    private suspend fun watchPedalSetting() {
        try {
            settings.settings.collect { stored ->
                mutableState.update { it.copy(pedalControl = stored.pedalControlOn) }
            }
        } catch (_: StorageUnavailableException) {
            // Without the setting the pedal stays a plain pedal.
        }
    }

    private suspend fun readPiano() {
        try {
            input.events(device).collect { event ->
                onPedal(event)
                when (event) {
                    is NoteOn -> onNoteOn(event)
                    is NoteOff -> onNoteOff(event)
                    else -> Unit
                }
            }
        } catch (_: MidiDeviceUnavailableException) {
            // The piano is gone: the screen says so.
        }
        if (!closed) mutableState.update { it.copy(connection = LessonConnection.DISCONNECTED) }
    }

    /** A pedal tap during the summary asks for Done, once; a press that began during a quiz does not count. */
    private fun onPedal(event: MidiEvent) {
        val current = mutableState.value
        if (current.summary == null) {
            pedal.reset()
        } else if (pedal.onEvent(event) && current.pedalControl && !doneAsked) {
            doneAsked = true
            doneRequests.trySend(Unit)
        }
    }

    private fun onNoteOn(event: NoteOn) {
        val note = event.note.number
        onNote(note, down = true) { it.onNoteOn(note, event.velocity, event.timestampNanos / NANOS_PER_MILLI) }
    }

    private fun onNoteOff(event: NoteOff) {
        val note = event.note.number
        onNote(note, down = false) { it.onNoteOff(note, event.timestampNanos / NANOS_PER_MILLI) }
    }

    /** Lights or releases [note] on the strip and applies [change] to the run (not during a count-in or the tick). */
    private fun onNote(
        note: Int,
        down: Boolean,
        change: (TryRun) -> TryRun,
    ) {
        if (mutableState.value.summary != null) return
        mutableState.update { it.copy(litKeys = if (down) it.litKeys + note else it.litKeys - note) }
        val before = run?.takeIf { accepting && !settling } ?: return
        val after = change(before)
        run = after
        mutableState.update { it.copy(progress = after.progress) }
        if (after.progress.finished) settle(skipped = false)
    }
}
