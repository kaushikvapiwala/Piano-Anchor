package pianoanchor.engine.matcher

import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage

/**
 * Where the learner is in [passage] (`docs/algorithms/wait-mode-matcher.md`).
 *
 * [stepIndex] is the step being played; [pressed] holds its notes already pressed in the open chord window, which
 * opened at [windowStartNanos] (null when nothing is pressed). [startedAtNanos] is the first NoteOn of the attempt
 * (null before it); [complete] is true once the last step was played.
 */
public data class MatchState(
    val passage: Passage,
    val stepIndex: Int,
    val pressed: Set<MidiNote>,
    val windowStartNanos: Long?,
    val startedAtNanos: Long?,
    val complete: Boolean,
)

/** One [WaitModeMatcher.reduce] result: the next [state] and the [events] the input caused, in order. */
public data class MatchStep(
    val state: MatchState,
    val events: List<MatchEvent>,
)
