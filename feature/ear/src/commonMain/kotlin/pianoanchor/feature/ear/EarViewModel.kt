package pianoanchor.feature.ear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.EarChoice
import pianoanchor.core.model.EarExercise
import pianoanchor.core.model.EarItemOutcome
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Skill
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.Tone
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.core.ports.TonePort
import pianoanchor.engine.Tunables
import pianoanchor.engine.ear.EarEcho
import pianoanchor.engine.ear.EarGenerator
import pianoanchor.engine.ear.EarGrading
import pianoanchor.engine.ear.tones
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.learner.learnerDay
import pianoanchor.engine.midi.MidiEncoder
import kotlin.random.Random
import kotlin.time.Clock

private const val CHANNEL = 0

/** How many times a draw is repeated to get an item not yet in the set, before an equal one is accepted. */
private const val MAX_REDRAWS = 20

/** One item on show and what the learner has done with it. */
private class ItemRun(
    val exercise: EarExercise,
) {
    var replays = 0
    var answers = 0
    var outcome: EarItemOutcome? = null
    var leaving = false
}

/** A NoteOn or NoteOff of the MIDI playback, [atMs] after it started. */
private class MidiAction(
    val atMs: Long,
    val note: MidiNote,
    val on: Boolean,
)

/**
 * The ear-training session (`docs/algorithms/ear-training.md`): runs [skills] in order, each a set of
 * `EAR_ITEMS_PER_REVIEW` items drawn with [random]. An item plays by itself on [device] (NoteOn and NoteOff only)
 * when [output] lists it, else on the speaker through [tone]; a MIDI send that fails moves the rest of the session to
 * the speaker. Identify items are answered with [answer]; echo items by playing the piano, read from [input] only
 * while an echo item waits for its answer. Each finished skill is recorded with [learner] on the learner day of [clock]
 * in [zone] (id from [newId]); leaving mid-way keeps those and records nothing for the current one.
 *
 * An input has one reader at a time: [close] returns once the piano is closed.
 */
@Suppress("LongParameterList", "TooManyFunctions") // Ports to wire; one small function per step of the session.
public class EarViewModel(
    private val device: MidiDevice,
    private val skills: List<Skill>,
    private val input: MidiInputPort,
    private val output: MidiOutputPort,
    private val tone: TonePort,
    private val learner: LearnerModel,
    private val clock: Clock,
    private val zone: TimeZone,
    private val random: Random,
    private val newId: () -> String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EarState(skillCount = skills.size))
    public val state: StateFlow<EarState> = mutableState.asStateFlow()

    /** The current step: recording a skill, drawing and playing an item, then listening for an echo. */
    private var work: Job? = null
    private var item: ItemRun? = null
    private var skillIndex = 0
    private var itemIndex = 0
    private val drawn = ArrayList<EarExercise>()
    private val outcomes = ArrayList<EarItemOutcome>()
    private var firstTry = 0
    private var itemsDone = 0
    private var storageFailed = false
    private var speakerOnly = false

    init {
        launchWork { if (skills.isEmpty()) summarise() else showItem() }
    }

    /** Plays the item again (the Replay button), at most `EAR_MAX_REPLAYS` times; not an answer. */
    public fun replay() {
        val run = item ?: return
        if (run.outcome != null || run.replays >= Tunables.EAR_MAX_REPLAYS) return
        run.replays++
        mutableState.update { it.copy(replaysLeft = Tunables.EAR_MAX_REPLAYS - run.replays) }
        launchWork { present(run) }
    }

    /** Answers the identify item with [choice]; ignored for an echo item or one that is over. */
    public fun answer(choice: EarChoice) {
        val run = item ?: return
        val exercise = run.exercise as? EarExercise.Identify ?: return
        if (run.outcome == null) judge(run, choice == exercise.answer)
    }

    /** Gives up the item after a first wrong answer: plays it and shows the answer; the item is MISSED. */
    public fun showMe() {
        val run = item ?: return
        if (run.outcome == null && run.answers > 0) miss(run)
    }

    /** Moves on after an item whose answer was shown. */
    public fun next() {
        val run = item ?: return
        if (run.outcome == EarItemOutcome.MISSED) moveOn(run)
    }

    /** Stops the session and returns once the piano is closed and the sound has stopped. */
    public suspend fun close() {
        withContext(NonCancellable) { work?.cancelAndJoin() }
    }

    /**
     * Runs [block] as the current step once the previous one has ended and its reader is closed. The wait can't be
     * cancelled, so a quick second tap can't open the piano while an earlier reader is still open.
     */
    private fun launchWork(block: suspend () -> Unit) {
        val previous = work
        work =
            viewModelScope.launch {
                withContext(NonCancellable) { previous?.cancelAndJoin() }
                block()
            }
    }

    private suspend fun showItem() {
        val skill = skills[skillIndex]
        val exercise =
            generateSequence { EarGenerator.draw(skill.id, random) }
                .take(MAX_REDRAWS)
                .firstOrNull { it !in drawn } ?: EarGenerator.draw(skill.id, random)
        drawn += exercise
        val run = ItemRun(exercise)
        item = run
        mutableState.value =
            EarState(
                skillTitle = skill.title,
                skillNumber = skillIndex + 1,
                skillCount = skills.size,
                itemNumber = itemIndex + 1,
                exercise = exercise,
                storageFailed = storageFailed,
            )
        present(run)
    }

    /** Plays the item, then (echo) listens for the learner's answer. */
    private suspend fun present(run: ItemRun) {
        play(run.exercise)
        if (run.exercise is EarExercise.Echo) listen(run, run.exercise)
    }

    /** Counts one answer: right ends the item, a second wrong one shows the answer, a first wrong one asks again. */
    private fun judge(
        run: ItemRun,
        correct: Boolean,
    ) {
        run.answers++
        when {
            correct -> {
                run.outcome =
                    if (run.answers == 1 && run.replays == 0) EarItemOutcome.FIRST_TRY else EarItemOutcome.RECOVERED
                moveOn(run)
            }

            run.answers >= Tunables.EAR_MAX_ANSWERS -> {
                miss(run)
            }

            else -> {
                mutableState.update { it.copy(feedback = EarFeedback.TRY_AGAIN) }
            }
        }
    }

    private fun miss(run: ItemRun) {
        run.outcome = EarItemOutcome.MISSED
        mutableState.update { it.copy(feedback = EarFeedback.REVEALED) }
        launchWork { play(run.exercise) }
    }

    /** Records the outcome of [run] once and goes on to the next item, or the end of the skill. */
    private fun moveOn(run: ItemRun) {
        if (run.leaving) return
        run.leaving = true
        outcomes += checkNotNull(run.outcome)
        launchWork {
            itemIndex++
            if (itemIndex < Tunables.EAR_ITEMS_PER_REVIEW) showItem() else finishSkill()
        }
    }

    /** Rates the set, records it (even when leaving now) and starts the next skill or the summary. */
    private suspend fun finishSkill() {
        val band = EarGrading.band(outcomes)
        firstTry += outcomes.count { it == EarItemOutcome.FIRST_TRY }
        itemsDone += outcomes.size
        withContext(NonCancellable) {
            try {
                learner.recordSkill(skills[skillIndex].id, band, learnerDay(clock.now(), zone), "ear:${newId()}")
            } catch (_: StorageUnavailableException) {
                storageFailed = true
            }
        }
        outcomes.clear()
        drawn.clear()
        itemIndex = 0
        skillIndex++
        if (skillIndex < skills.size) showItem() else summarise()
    }

    private fun summarise() {
        item = null
        mutableState.value =
            EarState(
                skillCount = skills.size,
                summary = EarSummary(skillIndex, firstTry, itemsDone),
                storageFailed = storageFailed,
            )
    }

    /** Reads the piano until the item is over: each run of one NoteOn per target note is an answer. */
    private suspend fun listen(
        run: ItemRun,
        echo: EarExercise.Echo,
    ) {
        val target = echo.sounds.sumOf { it.size }
        val attempt = ArrayList<NoteOn>()
        try {
            input.events(device).filterIsInstance<NoteOn>().firstOrNull { note ->
                attempt += note
                if (attempt.size == target) {
                    val right = EarEcho.matches(echo, attempt)
                    attempt.clear()
                    judge(run, right)
                }
                run.outcome != null
            }
        } catch (_: MidiDeviceUnavailableException) {
            // The piano is gone: nothing can be answered; leaving is the way out.
        }
    }

    private suspend fun play(exercise: EarExercise) {
        mutableState.update { it.copy(playing = true) }
        try {
            if (!speakerOnly && output.outputDevices.first().any { it.id == device.id }) {
                try {
                    playOnPiano(exercise.tones())
                    return
                } catch (_: MidiDeviceUnavailableException) {
                    speakerOnly = true
                }
            }
            tone.play(exercise.tones())
        } finally {
            mutableState.update { it.copy(playing = false) }
        }
    }

    /** NoteOn and NoteOff for each tone, at their times; a cancelled playback releases the keys still down. */
    private suspend fun playOnPiano(tones: List<Tone>) {
        val actions =
            tones
                .flatMap {
                    listOf(
                        MidiAction(it.startMs, it.note, true),
                        MidiAction(it.startMs + it.lengthMs, it.note, false),
                    )
                }.sortedWith(compareBy({ it.atMs }, { it.on }))
        val down = HashSet<MidiNote>()
        var at = 0L
        try {
            for (action in actions) {
                delay(action.atMs - at)
                at = action.atMs
                send(action.note, action.on)
                if (action.on) down += action.note else down -= action.note
            }
        } finally {
            withContext(NonCancellable) {
                for (note in down) {
                    try {
                        send(note, on = false)
                    } catch (_: MidiDeviceUnavailableException) {
                        // Already gone: nothing is sounding.
                    }
                }
            }
        }
    }

    private suspend fun send(
        note: MidiNote,
        on: Boolean,
    ) {
        val message =
            if (on) NoteOn(0, CHANNEL, note, Tunables.PLAYBACK_VELOCITY) else NoteOff(0, CHANNEL, note, 0)
        output.send(device, MidiEncoder.encode(message))
    }
}
