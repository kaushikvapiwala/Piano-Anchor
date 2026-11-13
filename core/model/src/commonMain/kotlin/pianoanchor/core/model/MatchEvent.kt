package pianoanchor.core.model

/**
 * What the wait-mode matcher saw while the learner played a [Passage]. [timestampNanos] is the onset of the note that
 * caused it (same clock as [MidiEvent.timestampNanos]); [stepIndex] is the step being played when it happened.
 */
public sealed interface MatchEvent {
    public val timestampNanos: Long
    public val stepIndex: Int
}

/** Every note of step [stepIndex] was pressed within the chord window; the matcher moved on. */
public data class StepCorrect(
    override val timestampNanos: Long,
    override val stepIndex: Int,
) : MatchEvent

/** [note] was pressed but isn't in step [stepIndex]; progress is unchanged. */
public data class WrongNote(
    override val timestampNanos: Long,
    override val stepIndex: Int,
    val note: MidiNote,
) : MatchEvent

/** The chord window of step [stepIndex] ran out with [missing] notes unpressed; the step must be played again. */
public data class ChordIncomplete(
    override val timestampNanos: Long,
    override val stepIndex: Int,
    val missing: Set<MidiNote>,
) : MatchEvent

/** The last step, [stepIndex], was played correctly: the passage is done. */
public data class PassageComplete(
    override val timestampNanos: Long,
    override val stepIndex: Int,
) : MatchEvent
