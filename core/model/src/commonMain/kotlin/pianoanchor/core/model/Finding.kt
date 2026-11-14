package pianoanchor.core.model

/** The kinds of error the performance analyser reports (`docs/algorithms/performance-analysis.md`). */
public enum class ErrorKind {
    /** A different key, 3–11 semitones away, instead of the score note. */
    WRONG_NOTE,

    /** A score note of a single-note step that was not played. */
    MISSED_NOTE,

    /** A key the score does not ask for, away from any score note in pitch and time. */
    EXTRA_NOTE,

    /** A chord step played with one of its notes left out. */
    INCOMPLETE_CHORD,

    /** A softer key one or two semitones from a correct note, at the same moment (a brushed key). */
    MISTOUCH,

    /** The score note played an octave too high or too low. */
    OCTAVE,

    /** The score note started too early for the tempo. */
    EARLY,

    /** The score note started too late for the tempo. */
    LATE,

    /** A pause before a step much longer than the music asks for. */
    HESITATION,

    /** The score note let go well before its written length. */
    TOO_SHORT,

    /** The score note held well past its written length. */
    TOO_LONG,
}

/** A note of the score: the step at [stepIndex] (>= 0) and the [pitch] within it. */
public data class ScoreNoteRef(
    val stepIndex: Int,
    val pitch: MidiNote,
) {
    init {
        require(stepIndex >= 0) { "step index must not be negative, was $stepIndex" }
    }
}

/**
 * One error the analyser found, at the score note it concerns. [ErrorKind.EXTRA_NOTE] and [ErrorKind.MISTOUCH]
 * point at the score note nearest in time; [ErrorKind.HESITATION] at the lowest pitch of the delayed step. [played]
 * is the played note involved, null for [ErrorKind.MISSED_NOTE] and [ErrorKind.INCOMPLETE_CHORD].
 */
public data class Finding(
    val kind: ErrorKind,
    val at: ScoreNoteRef,
    val played: PlayedNote?,
)
