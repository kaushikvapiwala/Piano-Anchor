package pianoanchor.engine.matcher

import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.SysEx
import pianoanchor.core.model.WrongNote
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Passages
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MS = 1_000_000L

class WaitModeMatcherTest {
    private val c4 = MidiNote(60)
    private val cs4 = MidiNote(61)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val g4 = MidiNote(67)
    private val chord = passageOf(setOf(c4, e4, g4), setOf(d4), id = "test:ceg", title = "C major")
    private val window = Tunables.CHORD_CLUSTER_MS * MS

    private fun on(
        t: Long,
        note: MidiNote,
        channel: Int = 0,
        velocity: Int = 80,
    ) = NoteOn(t, channel, note, velocity)

    /** Folds [events] through the matcher from the start of [passage]; returns the final state and all match events. */
    private fun run(
        passage: Passage,
        vararg events: MidiEvent,
    ): Pair<MatchState, List<MatchEvent>> {
        var state = WaitModeMatcher.start(passage)
        val out = ArrayList<MatchEvent>()
        events.forEach {
            val step = WaitModeMatcher.reduce(state, it)
            state = step.state
            out += step.events
        }
        return state to out
    }

    @Test
    fun startIsAtTheFirstStepWithNothingPressed() {
        val state = WaitModeMatcher.start(chord)
        assertEquals(MatchState(chord, 0, emptySet(), null, null, false), state)
    }

    @Test
    fun cdePlayedCleanlyCompletesAtE() {
        val (state, events) = run(Passages.CDE, on(1 * MS, c4), on(500 * MS, d4), on(1_000 * MS, e4))
        assertEquals(
            listOf(
                StepCorrect(1 * MS, 0),
                StepCorrect(500 * MS, 1),
                StepCorrect(1_000 * MS, 2),
                PassageComplete(1_000 * MS, 2),
            ),
            events,
        )
        assertTrue(state.complete)
        assertEquals(1 * MS, state.startedAtNanos)
    }

    @Test
    fun wrongNoteBetweenDAndEIsReportedAndThePassageStillCompletes() {
        val (state, events) = run(Passages.CDE, on(0, c4), on(100 * MS, d4), on(200 * MS, cs4), on(300 * MS, e4))
        assertEquals(
            listOf(
                StepCorrect(0, 0),
                StepCorrect(100 * MS, 1),
                WrongNote(200 * MS, 2, cs4),
                StepCorrect(300 * MS, 2),
                PassageComplete(300 * MS, 2),
            ),
            events,
        )
        assertTrue(state.complete)
    }

    @Test
    fun chordWithinTheWindowInAnyOrderIsOneStepCorrectAtTheLastOnset() {
        val (state, events) = run(chord, on(10 * MS, g4), on(30 * MS, c4), on(70 * MS, e4))
        assertEquals(listOf(StepCorrect(70 * MS, 0)), events)
        assertEquals(MatchState(chord, 1, emptySet(), null, 10 * MS, false), state)
    }

    @Test
    fun noteJustAfterTheWindowReportsTheIncompleteChordAndStartsAFreshOne() {
        val late = window + 1 * MS
        val (state, events) = run(chord, on(0, c4), on(20 * MS, e4), on(late, g4))
        assertEquals(listOf(ChordIncomplete(late, 0, setOf(g4))), events)
        assertEquals(MatchState(chord, 0, setOf(g4), late, 0, false), state)
    }

    @Test
    fun noteExactlyAtTheWindowEdgeCountsAsInside() {
        val (_, events) = run(chord, on(0, c4), on(20 * MS, e4), on(window, g4))
        assertEquals(listOf(StepCorrect(window, 0)), events)
    }

    @Test
    fun reStrikingAPressedNoteInsideTheWindowIsIgnored() {
        val (state, events) = run(chord, on(0, c4), on(40 * MS, c4))
        assertEquals(emptyList(), events)
        assertEquals(MatchState(chord, 0, setOf(c4), 0, 0, false), state)
    }

    @Test
    fun wrongNoteDuringAPartialChordLeavesTheChordOpen() {
        val (state, events) = run(chord, on(0, c4), on(20 * MS, cs4), on(40 * MS, e4), on(60 * MS, g4))
        assertEquals(listOf(WrongNote(20 * MS, 0, cs4), StepCorrect(60 * MS, 0)), events)
        assertEquals(1, state.stepIndex)
    }

    @Test
    fun wrongNoteAfterTheWindowFirstReportsTheIncompleteChord() {
        val late = window + 1 * MS
        val (state, events) = run(chord, on(0, c4), on(late, cs4))
        assertEquals(listOf(ChordIncomplete(late, 0, setOf(e4, g4)), WrongNote(late, 0, cs4)), events)
        assertEquals(MatchState(chord, 0, emptySet(), null, 0, false), state)
    }

    @Test
    fun nonNoteOnEventsChangeNothing() {
        val started = WaitModeMatcher.reduce(WaitModeMatcher.start(chord), on(0, c4)).state
        listOf(
            NoteOff(10 * MS, 0, c4, 64),
            ControlChange(10 * MS, 0, 64, 127),
            SysEx(10 * MS, listOf(0x7E)),
        ).forEach { assertEquals(MatchStep(started, emptyList()), WaitModeMatcher.reduce(started, it)) }
    }

    @Test
    fun noteOnFromAnyChannelAndVelocityCounts() {
        val (_, events) = run(Passages.CDE, on(0, c4, channel = 15, velocity = 1), on(1 * MS, d4, velocity = 127))
        assertEquals(listOf(StepCorrect(0, 0), StepCorrect(1 * MS, 1)), events)
    }

    @Test
    fun eventsAfterCompletionAreIgnored() {
        val (done, _) = run(Passages.CDE, on(0, c4), on(1 * MS, d4), on(2 * MS, e4))
        assertEquals(MatchStep(done, emptyList()), WaitModeMatcher.reduce(done, on(3 * MS, cs4)))
        assertEquals(MatchStep(done, emptyList()), WaitModeMatcher.reduce(done, on(3 * MS, e4)))
    }

    @Test
    fun firstNoteOnSetsTheStartEvenWhenWrong() {
        val (state, events) = run(Passages.CDE, on(5 * MS, cs4), on(9 * MS, c4))
        assertEquals(listOf(WrongNote(5 * MS, 0, cs4), StepCorrect(9 * MS, 0)), events)
        assertEquals(5 * MS, state.startedAtNanos)
    }

    @Test
    fun startedAtIsNullBeforeAnyNoteOn() {
        val (state, _) = run(Passages.CDE, NoteOff(0, 0, c4, 0))
        assertNull(state.startedAtNanos)
        assertFalse(state.complete)
    }
}
