package pianoanchor.engine.matcher

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.WrongNote
import pianoanchor.engine.Tunables

/**
 * Follows the learner through a [Passage] in wait mode: it moves on only when the current step's notes are all
 * pressed within [Tunables.CHORD_CLUSTER_MS], and reports wrong notes and incomplete chords. A pure reducer with no
 * clock: time comes only from event timestamps, so a chord window's expiry is noticed at the next NoteOn. Rules:
 * `docs/algorithms/wait-mode-matcher.md`.
 */
public object WaitModeMatcher {
    /** The state before the learner plays anything: first step, nothing pressed. */
    public fun start(passage: Passage): MatchState = MatchState(passage, 0, emptySet(), null, null, false)

    /** Applies [event] to [state]. Only [NoteOn] matters; other events, and any event once complete, change nothing. */
    public fun reduce(
        state: MatchState,
        event: MidiEvent,
    ): MatchStep {
        if (state.complete || event !is NoteOn) return MatchStep(state, emptyList())
        val t = event.timestampNanos
        val events = ArrayList<MatchEvent>()
        var next = state.copy(startedAtNanos = state.startedAtNanos ?: t)
        val step = next.passage.steps[next.stepIndex].notes
        val windowStart = next.windowStartNanos
        if (windowStart != null && t - windowStart > CHORD_WINDOW_NANOS) {
            events += ChordIncomplete(t, next.stepIndex, step - next.pressed)
            next = next.copy(pressed = emptySet(), windowStartNanos = null)
        }
        val note = event.note
        when {
            note !in step -> events += WrongNote(t, next.stepIndex, note)
            note !in next.pressed -> next = press(next, step, event, events) // a re-strike inside the window is ignored
        }
        return MatchStep(next, events)
    }

    /** Adds [event]'s note (in [step], not yet pressed) to [state]; on the step's last note, moves on. */
    private fun press(
        state: MatchState,
        step: Set<MidiNote>,
        event: NoteOn,
        events: MutableList<MatchEvent>,
    ): MatchState {
        val t = event.timestampNanos
        val pressed = state.pressed + event.note
        if (pressed != step) return state.copy(pressed = pressed, windowStartNanos = state.windowStartNanos ?: t)
        events += StepCorrect(t, state.stepIndex)
        val last = state.stepIndex == state.passage.steps.lastIndex
        if (last) events += PassageComplete(t, state.stepIndex)
        return state.copy(
            stepIndex = if (last) state.stepIndex else state.stepIndex + 1,
            pressed = emptySet(),
            windowStartNanos = null,
            complete = last,
        )
    }
}

/** Runs [WaitModeMatcher] over [this] for [passage]; completes right after emitting [PassageComplete]. */
public fun Flow<MidiEvent>.matchWaitMode(passage: Passage): Flow<MatchEvent> =
    flow {
        var state = WaitModeMatcher.start(passage)
        emitAll(
            transformWhile { event ->
                val step = WaitModeMatcher.reduce(state, event)
                state = step.state
                step.events.forEach { emit(it) }
                !state.complete
            },
        )
    }

private const val NANOS_PER_MILLI = 1_000_000L
private const val CHORD_WINDOW_NANOS = Tunables.CHORD_CLUSTER_MS * NANOS_PER_MILLI
