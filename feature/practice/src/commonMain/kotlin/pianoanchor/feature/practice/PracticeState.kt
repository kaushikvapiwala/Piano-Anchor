package pianoanchor.feature.practice

import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.HintLevel
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.engine.guidance.WrongKeyHint
import pianoanchor.engine.matcher.TempoRung

private const val COUNT_IN_NUMERALS = 3

/**
 * What the wait-mode practice screen shows. [passage] is the music practised (title, rhythm and time signature for
 * the staff); [steps] mirror its steps with each step's status; [currentStep] is the
 * step the app waits for (the last one once the passage is done); [keysDown] are the keys held right now, and
 * [wrongKeys] those of them that were wrong when pressed. [flash] is
 * the last pressed key for `Tunables.KEY_FLASH_MS`; [lastKey] is the same key until the next one is pressed (the
 * key strip's "last key" text). [finishedAttemptId] is set once the completed attempt is saved: the screen then
 * goes to Results. [saveFailed] is true while a completed attempt could not be saved (the screen offers Retry and
 * Discard). [steps] has one entry per step of [passage].
 *
 * Tempo mode: [rung] is the selected rung of the tempo ladder and [doneRungs] those already played clean. [phase] is
 * where a timed run is (always [RunPhase.READY] in wait mode); [beat] is the beat in the bar for the pulse (1-based, 0
 * before the first click and outside a run); [clickOn] is whether the metronome clicks; [liveMarks] are the live
 * judge's verdicts so far. In a timed run [currentStep] is the cursor and only that step is [StepStatus.WAITING].
 *
 * Key hints (`docs/algorithms/guidance.md`): [hintLevel] is the level the run started at and [hintVisible] whether the
 * amber band shows on the waiting step's keys right now (the screen keeps it false until the run's level is read).
 * [mode] is practice or perform; a perform take (always on a timed rung) shows no band, marks, tags or verdict text
 * ([showsVerdicts] is false) and lights pressed keys neutrally.
 * [spotLoop] is set while the screen loops a spot of the passage (`docs/algorithms/guidance.md`): [passage] is then
 * the slice.
 * [restartReason] says why a timed run just stopped and starts again from the top (a wrong or missed note); it shows
 * until the run arms again. After enough restarts in a row [offerSlower] shows the "too hard" card (Slower,
 * Keep trying) instead of arming again.
 * [linkResumed] is true from the moment a timed run was restarted because the piano's link dropped and came back
 * (messages in the gap were lost) until the next note; the screen says why the run started over.
 *
 * Gentle feedback (`docs/algorithms/wrong-key-hint.md`): after a wrong key in wait mode [hint] says how to find the
 * expected key and [hintGlow] holds the keys that glow in the accent until played. [playNow] is true for the one beat
 * after the count-in, when the screen says "Play!". [canListen] is true when the passage can be played for the learner
 * ([PassagePlayer]); [listening] while it does, with [demoStep] the step sounding and [demoKeys] its keys.
 * A Unit 0 passage hides the staff ([staffHidden], set by the ViewModel) and glows every next key while a run is
 * under way. [played] is true once a key was pressed in the run (the Listen button then goes).
 * A [sightReading] exercise runs in wait mode from the staff alone: the screen hides the tempo chips, the perform
 * switch and the Hint button; so does a placement piece. [finishedBand] is the band of the attempt that set
 * [finishedAttemptId].
 */
public data class PracticeState(
    val passage: Passage,
    val steps: List<StepView>,
    val currentStep: Int = 0,
    val keysDown: Set<MidiNote> = emptySet(),
    val wrongKeys: Set<MidiNote> = emptySet(),
    val flash: KeyFlash? = null,
    val lastKey: KeyFlash? = null,
    val connection: PracticeConnection = PracticeConnection.CONNECTED,
    val finishedAttemptId: String? = null,
    val saveFailed: Boolean = false,
    val rung: TempoRung = TempoRung.WAIT,
    val doneRungs: Set<TempoRung> = emptySet(),
    val phase: RunPhase = RunPhase.READY,
    val beat: Int = 0,
    val clickOn: Boolean = true,
    val liveMarks: Map<ScoreNoteRef, LiveMark> = emptyMap(),
    val hintLevel: HintLevel = HintLevel.FULL,
    val hintVisible: Boolean = true,
    val mode: RunMode = RunMode.PRACTICE,
    val spotLoop: SpotLoopState? = null,
    val linkResumed: Boolean = false,
    val restartReason: RestartReason? = null,
    val offerSlower: Boolean = false,
    val hint: WrongKeyHint? = null,
    val hintGlow: Set<MidiNote> = emptySet(),
    val playNow: Boolean = false,
    val canListen: Boolean = false,
    val listening: Boolean = false,
    val demoStep: Int? = null,
    val demoKeys: Set<MidiNote> = emptySet(),
    val staffHidden: Boolean = false,
    val played: Boolean = false,
    val sightReading: Boolean = false,
    val finishedBand: Band? = null,
) {
    init {
        require(steps.size == passage.steps.size) {
            "practice state has ${steps.size} step views for ${passage.steps.size} steps of \"${passage.id}\""
        }
    }

    /** Whether the screen shows live verdicts (colours, timing tags, the last key's text) and the Hint button. */
    val showsVerdicts: Boolean get() = mode == RunMode.PRACTICE

    /** The keys of the waiting step, empty when no step waits. */
    val waitingNotes: Set<MidiNote>
        get() =
            steps
                .getOrNull(currentStep)
                ?.takeIf { it.status == StepStatus.WAITING }
                ?.notes
                .orEmpty()

    /** Keys that glow in the accent: those a wrong key points to and, in Unit 0, every next key at any hint level. */
    val glowKeys: Set<MidiNote> get() =
        if (staffHidden && showsVerdicts &&
            underway
        ) {
            hintGlow + waitingNotes
        } else {
            hintGlow
        }

    /** Whether a run is reading the piano: wait mode when not listening, a timed run once the cursor moves. */
    private val underway: Boolean get() = !listening && (rung == TempoRung.WAIT || phase == RunPhase.PLAYING)

    /** The steps as the staff draws them: while the passage plays for the learner the cursor follows the sound. */
    val shownSteps: List<StepView>
        get() {
            val playing = demoStep ?: return steps
            return steps.mapIndexed { i, step ->
                step.copy(
                    status =
                        when {
                            i < playing -> StepStatus.DONE
                            i == playing -> StepStatus.WAITING
                            else -> StepStatus.UPCOMING
                        },
                )
            }
        }

    /** The large count-in numeral ("3", "2", "1" on the last three beats of the count-in), null outside them. */
    val countInNumeral: Int?
        get() =
            if (phase == RunPhase.COUNT_IN && beat > 0) {
                (passage.timeSignature.beats - beat + 1).takeIf { it <= COUNT_IN_NUMERALS }
            } else {
                null
            }

    /** Whether the Listen button shows: the run has not started and nothing is playing. */
    val showsListen: Boolean
        get() =
            canListen && !listening && !played && phase == RunPhase.READY && currentStep == 0 &&
                !saveFailed && finishedAttemptId == null && connection == PracticeConnection.CONNECTED

    public companion object {
        /** The state before the first note of [passage]: its first step waiting, the rest upcoming. */
        public fun start(passage: Passage): PracticeState =
            PracticeState(
                passage = passage,
                steps =
                    passage.steps.mapIndexed { i, step ->
                        StepView(step.pitches, if (i == 0) StepStatus.WAITING else StepStatus.UPCOMING)
                    },
            )
    }
}

/**
 * A spot loop of [bars]: [cleanLoops] CLEAN loops in a row so far, the [lastLoop] result (null before the first loop
 * ends) and, once the target is reached, [offerFull] (the card that offers the whole piece). [fullRung] is the rung
 * the run that started the loop was on: "Play it" plays the full passage there.
 */
public data class SpotLoopState(
    val bars: BarRange,
    val fullRung: TempoRung,
    val cleanLoops: Int = 0,
    val lastLoop: LoopResult? = null,
    val offerFull: Boolean = false,
)

/** How a finished spot loop went: [clean], else its [wrongNotes] and [incompleteChords]. */
public data class LoopResult(
    val clean: Boolean,
    val wrongNotes: Int,
    val incompleteChords: Int,
)

/** One step of the passage on the staff: its [notes] and where the learner is relative to it. */
public data class StepView(
    val notes: Set<MidiNote>,
    val status: StepStatus,
)

/** A step's place in the run. */
public enum class StepStatus {
    /** Played correctly. */
    DONE,

    /** The step the app waits for. */
    WAITING,

    /** Still to come. */
    UPCOMING,
}

/**
 * A pressed [note] and whether it was [correct] (part of the step the app waited for; in a timed run the right note,
 * early or late). [deviationMs] is how far off time a correct timed note was (+ late, - early), null when on time or
 * in wait mode.
 */
public data class KeyFlash(
    val note: MidiNote,
    val correct: Boolean,
    val deviationMs: Int? = null,
)

/** Where a timed run is. Wait mode stays [READY]. */
public enum class RunPhase {
    /** Arming: the run counts in by itself shortly (also the pause after a mistake). */
    READY,

    /** The one-bar count-in; [PracticeState.beat] counts it. */
    COUNT_IN,

    /** The cursor moves with the clock. */
    PLAYING,

    /** The run is over (graded, saved or waiting for a retry). */
    FINISHED,
}

/** Why a timed run stopped: a [kind] of mistake at [note] (the key played wrong, or the expected key missed). */
public data class RestartReason(
    val kind: RestartKind,
    val note: MidiNote,
)

/** The mistakes that stop a timed run. */
public enum class RestartKind {
    /** A key the step did not ask for. */
    WRONG,

    /** An expected key whose time passed unplayed. */
    MISSED,
}

/** What the live judge made of one score note. */
public sealed interface LiveMark {
    /** On time: drawn blue. */
    public data object Correct : LiveMark

    /** Right note, [deviationMs] off its time (+ late, - early): drawn hollow orange with a tag. */
    public data class TimingOff(
        val deviationMs: Int,
    ) : LiveMark
}

/** The link to the piano during the run. */
public enum class PracticeConnection {
    /** The piano's notes arrive. */
    CONNECTED,

    /** The piano's stream ended or failed; the run is discarded. */
    DISCONNECTED,
}
