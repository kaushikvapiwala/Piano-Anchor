package pianoanchor.feature.practice

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage

/**
 * What the wait-mode practice screen shows. [steps] mirror the passage with each step's status; [currentStep] is the
 * step the app waits for (the last one once the passage is done); [keysDown] are the keys held right now, and
 * [wrongKeys] those of them that were wrong when pressed. [flash] is
 * the last pressed key for `Tunables.KEY_FLASH_MS`; [lastKey] is the same key until the next one is pressed (the
 * key strip's "last key" text). [finishedAttemptId] is set once the completed attempt is saved: the screen then
 * goes to Results.
 */
public data class PracticeState(
    val passageTitle: String,
    val steps: List<StepView>,
    val currentStep: Int = 0,
    val keysDown: Set<MidiNote> = emptySet(),
    val wrongKeys: Set<MidiNote> = emptySet(),
    val flash: KeyFlash? = null,
    val lastKey: KeyFlash? = null,
    val connection: PracticeConnection = PracticeConnection.CONNECTED,
    val finishedAttemptId: String? = null,
) {
    public companion object {
        /** The state before the first note of [passage]: its first step waiting, the rest upcoming. */
        public fun start(passage: Passage): PracticeState =
            PracticeState(
                passageTitle = passage.title,
                steps =
                    passage.steps.mapIndexed { i, step ->
                        StepView(step.notes, if (i == 0) StepStatus.WAITING else StepStatus.UPCOMING)
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

/** A pressed [note] and whether it was [correct] (part of the step the app waited for). */
public data class KeyFlash(
    val note: MidiNote,
    val correct: Boolean,
)

/** The link to the piano during the run. */
public enum class PracticeConnection {
    /** The piano's notes arrive. */
    CONNECTED,

    /** The piano's stream ended or failed; the run is discarded. */
    DISCONNECTED,
}
