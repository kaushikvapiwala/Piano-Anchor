package pianoanchor.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pianoanchor.core.model.LessonStep
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Passage
import pianoanchor.core.model.SceneHand
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.Tone
import pianoanchor.core.model.TryCheck
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MetronomePort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.core.ports.TonePort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.CourseProgress
import pianoanchor.engine.lesson.Demo
import pianoanchor.engine.lesson.HandDemo
import pianoanchor.engine.lesson.LessonDemo
import pianoanchor.engine.lesson.NoteLabels
import pianoanchor.engine.lesson.NoteLabelsUseCase
import pianoanchor.engine.lesson.TryRun
import pianoanchor.engine.lesson.tryRepsNeeded
import pianoanchor.engine.matcher.beatNanos
import pianoanchor.engine.midi.PedalTapDetector
import pianoanchor.engine.midi.canPlayOn
import pianoanchor.engine.midi.playTones
import pianoanchor.engine.session.passageHistories

private const val NANOS_PER_MILLI = 1_000_000L
private const val BEATS_PER_BAR = 4

/** A moment in the lit-keys schedule: [note] goes down or up [atMs] after the demo began. */
private class KeyChange(
    val atMs: Long,
    val note: Int,
    val on: Boolean,
)

/**
 * The five-step lesson (Why, See, Try, Use, Recap) of the course concept [skillId], on [device]
 * (`docs/course/beginner-course.md`, "Lesson shape").
 *
 * The step is saved in [settings] whenever it changes (`Settings.lessonSteps`) and read back when the lesson opens, at
 * [startAt] instead when given; reaching Recap removes the entry. See plays the Try's demo ([LessonDemo]) on the piano
 * when [output] lists [device], else on the speaker through [tone] (a failed send moves on to the speaker), lighting
 * keys as they sound. Try reads the piano from [input], opened once for the screen, into a [TryRun]: a Try with a
 * click starts [metronome] with a one-bar count-in. A Try passes after `tryRepsNeeded` clean checks in a row (a wrong
 * key sets the count back to 0); each finished check shows its tick for `LESSON_TRY_DONE_PAUSE_MS` before the next
 * starts, and the passed Try moves on to Use. A concept whose Try is a passage has no Try step: it is part of Use.
 * Recap names the next unpassed concept, reading the passes from [attempts] and the placed skills from [learner];
 * [time] stamps the piano's notes.
 *
 * Hands-free: with `Settings.pedalControlOn` (and unless [pedalIsMusic], the concept uses the pedal as music) a pedal
 * tap on the piano ([PedalTapDetector]) does what the step's main button does, outside Try. Why moves on by itself
 * after `LESSON_WHY_AUTO_MS`, See when the demo ends (a demo with nothing to hear: after `LESSON_WHY_AUTO_MS`), Use
 * asks for Practice after `LESSON_AUTO_OPEN_MS` (not when the lesson was just resumed there); Recap waits. Where the
 * lesson must leave (Practice, the next lesson, Done) it sends a [LessonExit] on [exits], which the host acts on.
 *
 * An input has one reader at a time: [close] returns once the piano is closed and the sound has stopped, before
 * another screen (practice) opens the piano.
 */
@Suppress("LongParameterList", "TooManyFunctions") // Ports to wire; one small function per step of the lesson.
public class LessonViewModel(
    private val device: MidiDevice,
    private val skillId: String,
    private val input: MidiInputPort,
    private val output: MidiOutputPort,
    private val tone: TonePort,
    private val metronome: MetronomePort,
    private val settings: SettingsRepositoryPort,
    private val attempts: AttemptRepositoryPort,
    private val learner: LearnerModel,
    private val noteLabels: NoteLabelsUseCase,
    private val time: ClockPort,
    private val startAt: LessonStep? = null,
    private val pedalIsMusic: Boolean = false,
    private val usePassage: Passage? = usePassageOf(skillId),
) : ViewModel() {
    private val skill = requireNotNull(Curriculum.graph.byId(skillId)) { "unknown skill $skillId" }
    private val card = requireNotNull(Curriculum.card(skillId)) { "skill $skillId has no concept card" }
    private val mutableState =
        MutableStateFlow(
            LessonState(skill, card, LessonStep.WHY, null, false, emptySet(), null, LessonConnection.CONNECTED),
        )
    public val state: StateFlow<LessonState> = mutableState.asStateFlow()

    private val exitRequests = Channel<LessonExit>(Channel.BUFFERED)

    /** Where the lesson asks to go on a pedal tap or by itself; the host closes the lesson, then navigates. */
    public val exits: Flow<LessonExit> = exitRequests.receiveAsFlow()
    private val pedal = PedalTapDetector()
    private var exited = false

    private var reader: Job? = null

    /** What the current step does: plays the demo, or waits for the Try's notes. */
    private var work: Job? = null

    /** The pause that shows a passed Try's tick before moving on. */
    private var finishing: Job? = null
    private var run: TryRun? = null

    /** The clean checks in a row of the Try so far; a wrong key sets it back to 0. */
    private var cleanReps = 0

    /** False during a Try's count-in: notes are not counted. */
    private var accepting = false
    private var speakerOnly = false
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
        viewModelScope.launch {
            val saved = if (startAt == null) savedStep() else null
            // A lesson reopened at Use (back from Practice) stays there: it opens Practice only when the learner gets
            // to Use in this visit.
            enter(startAt ?: saved ?: LessonStep.WHY, persist = startAt != null, autoOpen = false)
        }
    }

    /** Jumps to [step] (a dot of the row); a Try that is part of Use goes to Use. */
    public fun goTo(step: LessonStep) {
        enter(step, persist = true)
    }

    /** Moves on to the next step; nothing happens in Recap. */
    public fun next() {
        LessonStep.entries.getOrNull(mutableState.value.step.ordinal + 1)?.let { enter(it, persist = true) }
    }

    /** Skips the rest of this step, stopping any sound: moves on like [next]. */
    public fun skip() {
        next()
    }

    /** Plays the See demo again, from the start. */
    public fun replaySee() {
        if (mutableState.value.step == LessonStep.SEE) enter(LessonStep.SEE, persist = false, auto = false)
    }

    /** Stops the lesson and returns once the piano is closed and the sound has stopped; later taps start nothing. */
    public suspend fun close() {
        closed = true
        withContext(NonCancellable) {
            finishing?.cancelAndJoin()
            work?.cancelAndJoin()
            reader?.cancelAndJoin()
        }
    }

    private suspend fun savedStep(): LessonStep =
        try {
            settings.settings.first().lessonSteps[skillId] ?: LessonStep.WHY
        } catch (_: StorageUnavailableException) {
            LessonStep.WHY
        }

    private fun enter(
        requested: LessonStep,
        persist: Boolean,
        autoOpen: Boolean = true,
        auto: Boolean = true,
    ) {
        if (closed) return
        val step = if (requested == LessonStep.TRY && mutableState.value.tryMergedIntoUse) LessonStep.USE else requested
        val previous = work
        finishing?.cancel()
        run = null
        accepting = false
        pedal.reset()
        mutableState.update {
            it.copy(
                step = step,
                tryProgress = null,
                playing = false,
                litKeys = emptySet(),
                recap = null,
                countIn = null,
                seeHand = null,
                seeElapsedMs = 0,
            )
        }
        if (persist) viewModelScope.launch { save(step) }
        work =
            viewModelScope.launch {
                // The previous step releases the piano's sound and the click first; this wait can't be cancelled.
                withContext(NonCancellable) { previous?.cancelAndJoin() }
                runStep(step, auto, autoOpen)
            }
    }

    /** What a step does by itself: the Why wait, the demo, the Try's listening, the Use wait, the recap. */
    private suspend fun runStep(
        step: LessonStep,
        auto: Boolean,
        autoOpen: Boolean,
    ) {
        when (step) {
            LessonStep.WHY -> {
                delay(Tunables.LESSON_WHY_AUTO_MS)
                next()
            }

            LessonStep.SEE -> {
                val demo = LessonDemo.of(checkOf())
                if (demo.tones.isEmpty()) {
                    if (auto) delay(Tunables.LESSON_WHY_AUTO_MS)
                } else {
                    playDemo(demo)
                }
                if (auto) next()
            }

            LessonStep.TRY -> {
                tryStep(checkOf())
            }

            LessonStep.USE -> {
                if (autoOpen && usePassage != null) {
                    delay(Tunables.LESSON_AUTO_OPEN_MS)
                    // A dropped piano can't play it: wait for the learner.
                    if (mutableState.value.connection == LessonConnection.CONNECTED) {
                        requestExit(LessonExit.PLAY_IT)
                    }
                }
            }

            LessonStep.RECAP -> {
                showRecap()
            }
        }
    }

    private fun checkOf(): TryCheck = requireNotNull(mutableState.value.check) { "skill $skillId has no Try check" }

    private suspend fun save(step: LessonStep) {
        try {
            settings.update {
                val done = step == LessonStep.RECAP
                it.copy(lessonSteps = if (done) it.lessonSteps - skillId else it.lessonSteps + (skillId to step))
            }
        } catch (_: StorageUnavailableException) {
            // The lesson goes on; it just won't resume at this step.
        }
    }

    // --- See ---

    private suspend fun playDemo(demo: Demo) {
        val hand =
            HandDemo.of(
                demo,
                mutableState.value.scene
                    ?.stage
                    ?.hand ?: SceneHand.RIGHT,
            )
        mutableState.update { it.copy(playing = true, seeHand = hand, seeElapsedMs = 0) }
        try {
            coroutineScope {
                launch { lightKeys(demo.tones) }
                demo.clickBpm?.let { bpm ->
                    val beat = beatNanos(bpm)
                    metronome.start(time.nowNanos() + demo.leadMs * NANOS_PER_MILLI, beat, BEATS_PER_BAR)
                }
                sound(demo.tones)
            }
        } finally {
            withContext(NonCancellable) {
                if (demo.clickBpm != null) metronome.stop()
                mutableState.update { it.copy(playing = false, litKeys = emptySet(), seeElapsedMs = 0) }
            }
        }
    }

    /** Plays on the piano when it is listed, else (or once a send fails) on the speaker, as the ear trainer does. */
    private suspend fun sound(tones: List<Tone>) {
        if (!speakerOnly && output.canPlayOn(device)) {
            try {
                output.playTones(device, tones)
                return
            } catch (_: MidiDeviceUnavailableException) {
                speakerOnly = true
            }
        }
        tone.play(tones)
    }

    /** Lights each key on the strip while its tone sounds. */
    private suspend fun lightKeys(tones: List<Tone>) {
        val changes =
            tones
                .flatMap {
                    listOf(
                        KeyChange(it.startMs, it.note.number, true),
                        KeyChange(it.startMs + it.lengthMs, it.note.number, false),
                    )
                }.sortedWith(compareBy({ it.atMs }, { it.on }))
        var at = 0L
        for (change in changes) {
            delay(change.atMs - at)
            at = change.atMs
            mutableState.update {
                it.copy(
                    litKeys = if (change.on) it.litKeys + change.note else it.litKeys - change.note,
                    seeElapsedMs = change.atMs,
                )
            }
        }
    }

    // --- Try ---

    private suspend fun tryStep(check: TryCheck) {
        cleanReps = 0
        mutableState.update { it.copy(cleanReps = 0, repsNeeded = tryRepsNeeded(check)) }
        try {
            beginRep(check)
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { if (clickOf(check) != null) metronome.stop() }
        }
    }

    /**
     * Starts one check of the Try from scratch. With a click, notes don't count during a one-bar count-in, so beat 1 of
     * every check lands on the click's downbeat; without one they count at once.
     */
    private suspend fun beginRep(check: TryCheck) {
        accepting = false
        run = TryRun(check)
        mutableState.update { it.copy(tryProgress = run?.progress) }
        val bpm = clickOf(check)
        if (bpm == null) {
            accepting = true
        } else {
            countIn(metronome, time, bpm, { n -> mutableState.update { it.copy(countIn = n) } }) { accepting = true }
        }
    }

    private suspend fun watchPedalSetting() {
        try {
            settings.settings.collect { stored ->
                mutableState.update { it.copy(pedalControl = stored.pedalControlOn && !pedalIsMusic) }
            }
        } catch (_: StorageUnavailableException) {
            // Without the setting the pedal stays a plain pedal.
        }
    }

    /** What a pedal tap does: the step's main button, never in Try (a Try listens for notes). */
    private fun onPedalTap() {
        val current = mutableState.value
        if (!current.pedalControl) return
        when (current.step) {
            LessonStep.WHY, LessonStep.SEE -> {
                next()
            }

            LessonStep.TRY -> {
                // A Try listens for notes, not the pedal.
            }

            LessonStep.USE -> {
                if (usePassage != null) requestExit(LessonExit.PLAY_IT)
            }

            LessonStep.RECAP -> {
                // Until the recap has loaded the next lesson is unknown: ignore the tap.
                val recap = current.recap ?: return
                requestExit(if (recap.nextSkillId != null) LessonExit.NEXT_LESSON else LessonExit.DONE)
            }
        }
    }

    /** Asks the host to leave once; the lesson then ignores further pedal taps and timers. */
    private fun requestExit(exit: LessonExit) {
        if (exited) return
        exited = true
        exitRequests.trySend(exit)
    }

    private suspend fun readPiano() {
        try {
            input.events(device).collect { event ->
                // A press that began in a Try is not a tap on the next step.
                if (mutableState.value.step == LessonStep.TRY) {
                    pedal.reset()
                } else if (pedal.onEvent(event)) {
                    onPedalTap()
                }
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

    private fun onNoteOn(event: NoteOn) {
        if (mutableState.value.step != LessonStep.TRY) return
        val note = event.note.number
        mutableState.update { it.copy(litKeys = it.litKeys + note) }
        advance { it.onNoteOn(note, event.velocity, event.timestampNanos / NANOS_PER_MILLI) }
    }

    private fun onNoteOff(event: NoteOff) {
        if (mutableState.value.step != LessonStep.TRY) return
        val note = event.note.number
        mutableState.update { it.copy(litKeys = it.litKeys - note) }
        advance { it.onNoteOff(note, event.timestampNanos / NANOS_PER_MILLI) }
    }

    /**
     * Applies [change] to the Try's run (not during the count-in). A wrong key resets the clean checks in a row; a
     * check finished with no wrong key adds one. After the tick's pause the Try is passed once
     * [LessonState.repsNeeded] are clean, else the next check starts.
     */
    private fun advance(change: (TryRun) -> TryRun) {
        val before = run?.takeIf { accepting } ?: return
        val after = change(before)
        run = after
        if (after.progress.wrongCount > before.progress.wrongCount) cleanReps = 0
        val finished = after.progress.finished && !before.progress.finished
        if (finished && after.progress.wrongCount == 0) cleanReps++
        mutableState.update { it.copy(tryProgress = after.progress, cleanReps = cleanReps) }
        if (finished) {
            finishing =
                viewModelScope.launch {
                    delay(Tunables.LESSON_TRY_DONE_PAUSE_MS)
                    if (cleanReps >= mutableState.value.repsNeeded) next() else beginRep(after.check)
                }
        }
    }

    // --- Recap ---

    private suspend fun showRecap() {
        val passed =
            try {
                CourseProgress.passed(
                    Curriculum,
                    attempts.passageHistories(Curriculum.passages),
                    learner.placedSkills(),
                )
            } catch (_: StorageUnavailableException) {
                // Without the passes, the next concept is the one after this in map order.
                CourseProgress.skills(Curriculum).takeWhile { it != skill }.mapTo(HashSet()) { it.id }
            }
        // This concept counts as done: the learner has just been through it.
        val next = CourseProgress.next(Curriculum, passed + skillId)
        mutableState.update { it.copy(recap = RecapText(skill.title, next?.title, next?.id)) }
    }
}
