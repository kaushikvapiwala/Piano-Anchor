package pianoanchor.feature.practice

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.engine.matcher.TempoRung

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
) {
    init {
        require(steps.size == passage.steps.size) {
            "practice state has ${steps.size} step views for ${passage.steps.size} steps of \"${passage.id}\""
        }
    }

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
    /** Waiting for the key that starts the count-in. */
    READY,

    /** The one-bar count-in; [PracticeState.beat] counts it. */
    COUNT_IN,

    /** The cursor moves with the clock. */
    PLAYING,

    /** The run is over (graded, saved or waiting for a retry). */
    FINISHED,
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
