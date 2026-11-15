package pianoanchor.core.model

/** The answers an ear item offers. Each ear skill uses two of them. */
public enum class EarChoice {
    HIGHER,
    LOWER,
    STEP,
    SKIP,
    MAJOR_THIRD,
    MINOR_THIRD,
    FOURTH,
    FIFTH,
    MAJOR_CHORD,
    MINOR_CHORD,
}

/** One ear item: something to hear, and how the learner answers it. */
public sealed interface EarExercise {
    /** What is played: groups in order; the notes of one group sound together (a chord). */
    public val sounds: List<Set<MidiNote>>

    /** Play [sounds] back on the piano. [exactOctave] false accepts the pitch classes in order, in any octave. */
    public data class Echo(
        override val sounds: List<Set<MidiNote>>,
        val exactOctave: Boolean,
    ) : EarExercise

    /** Tap the right [answer] among [choices] (2 for every M4 skill). */
    public data class Identify(
        override val sounds: List<Set<MidiNote>>,
        val choices: List<EarChoice>,
        val answer: EarChoice,
    ) : EarExercise
}

/** How an ear item ended: right on the first answer with no replay, right after a replay or a retry, or not. */
public enum class EarItemOutcome { FIRST_TRY, RECOVERED, MISSED }
