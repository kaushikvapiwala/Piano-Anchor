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
import kotlin.time.Duration.Companion.milliseconds

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
        var next = if (state.startedAtNanos == null) state.copy(startedAtNanos = t) else state
        val stepNotes = next.passage.steps[next.stepIndex].pitches
        val windowStart = next.windowStartNanos
        if (windowStart != null && t - windowStart > CHORD_WINDOW_NANOS) {
            events += ChordIncomplete(t, next.stepIndex, stepNotes - next.pressed)
            next = next.copy(pressed = emptySet(), windowStartNanos = null)
        }
        val note = event.note
        // A note of the step that is already pressed is a re-strike inside the window: ignored.
        when {
            note !in stepNotes -> events += WrongNote(t, next.stepIndex, note)
            note !in next.pressed -> next = press(next, stepNotes, event, events)
        }
        return MatchStep(next, events)
    }

    /** Adds [event]'s note (in [stepNotes], not yet pressed) to [state]; on the step's last note, moves on. */
    private fun press(
        state: MatchState,
        stepNotes: Set<MidiNote>,
        event: NoteOn,
        events: MutableList<MatchEvent>,
    ): MatchState {
        val t = event.timestampNanos
        val pressed = state.pressed + event.note
        if (pressed != stepNotes) return state.copy(pressed = pressed, windowStartNanos = state.windowStartNanos ?: t)
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
                val result = WaitModeMatcher.reduce(state, event)
                state = result.state
                result.events.forEach { emit(it) }
                !state.complete
            },
        )
    }

private val CHORD_WINDOW_NANOS: Long = Tunables.CHORD_CLUSTER_MS.milliseconds.inWholeNanoseconds
