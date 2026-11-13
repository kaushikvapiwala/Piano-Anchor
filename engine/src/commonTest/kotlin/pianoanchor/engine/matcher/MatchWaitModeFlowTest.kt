package pianoanchor.engine.matcher

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.WrongNote
import pianoanchor.engine.content.Passages
import pianoanchor.engine.fixture.FixtureCodec
import pianoanchor.engine.midi.MidiParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private const val MS = 1_000_000L

class MatchWaitModeFlowTest {
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)

    private fun on(
        t: Long,
        note: MidiNote,
    ) = NoteOn(t, 0, note, 80)

    @Test
    fun emitsWhatFoldingReduceGivesAndCompletesAfterPassageComplete() =
        runTest {
            val played = listOf(on(0, c4), on(1 * MS, MidiNote(61)), on(2 * MS, d4), on(3 * MS, e4))
            var state = WaitModeMatcher.start(Passages.CDE)
            val folded =
                played.flatMap { event ->
                    WaitModeMatcher.reduce(state, event).also { state = it.state }.events
                }
            var extraRequested = false
            val upstream =
                flow<MidiEvent> {
                    played.forEach { emit(it) }
                    extraRequested = true
                    emit(on(4 * MS, c4))
                }

            assertEquals(folded, upstream.matchWaitMode(Passages.CDE).toList())
            assertEquals(PassageComplete(3 * MS, 2), folded.last())
            assertFalse(extraRequested)
        }

    @Test
    fun upstreamEndingEarlyCompletesWithoutPassageComplete() =
        runTest {
            val events = listOf<MidiEvent>(on(0, c4)).asFlow().matchWaitMode(Passages.CDE).toList()
            assertEquals(listOf(StepCorrect(0, 0)), events)
        }

    @Test
    fun eachCollectionStartsFromTheFirstStep() =
        runTest {
            val matched = listOf<MidiEvent>(on(0, c4)).asFlow().matchWaitMode(Passages.CDE)
            assertEquals(matched.toList(), matched.toList())
        }

    @Test
    fun replayedFixtureGivesTheExpectedEvents() =
        runTest {
            val jsonl =
                """
                {"schema":1,"device":"test piano","recordedAt":"2026-11-13T00:00:00Z","clock":"monotonic-ns"}
                {"t":0,"bytes":"903c50"}
                {"t":200000000,"bytes":"903c00"}
                {"t":400000000,"bytes":"903d50"}
                {"t":500000000,"bytes":"903e50"}
                {"t":700000000,"bytes":"b04040"}
                {"t":900000000,"bytes":"904050"}
                """.trimIndent()
            val parser = MidiParser()
            val midi = FixtureCodec.decode(jsonl).messages.flatMap { parser.feed(it.bytes, it.t) }

            assertEquals(
                listOf(
                    StepCorrect(0, 0),
                    WrongNote(400 * MS, 1, MidiNote(61)),
                    StepCorrect(500 * MS, 1),
                    StepCorrect(900 * MS, 2),
                    PassageComplete(900 * MS, 2),
                ),
                midi.asFlow().matchWaitMode(Passages.CDE).toList(),
            )
        }
}
