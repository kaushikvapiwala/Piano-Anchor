package pianoanchor.engine.guidance

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.WrongNote
import pianoanchor.testing.TEST_TEMPO_BPM
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private const val NANOS_PER_BEAT = 1_000_000_000L // TEST_TEMPO_BPM = 60

class SpotFinderTest {
    private val c4 = MidiNote(60)

    /** [bars] bars of 4/4 quarter notes, every step a C4. */
    private fun passage(bars: Int): Passage = passageOf(*Array(bars * 4) { setOf(c4) })

    private fun waitRun(vararg events: MatchEvent) = attempt(events.toList(), tempo = null, notes = emptyList())

    private fun attempt(
        events: List<MatchEvent>,
        tempo: Int?,
        notes: List<PlayedNote>,
        latency: Long? = null,
    ) = Attempt(
        id = "a",
        passageId = "test:passage",
        deviceId = "d",
        startedAt = Instant.fromEpochSeconds(0),
        durationNanos = 1,
        stepCount = 1,
        wrongNotes = 0,
        incompleteChords = 0,
        accuracy = 1.0,
        band = Band.GOOD,
        events = events,
        tempoBpm = tempo,
        playedNotes = notes,
        latencyOffsetNanos = latency,
    )

    private fun wrong(step: Int) = WrongNote(step.toLong(), step, MidiNote(61))

    @Test
    fun `picks the two-bar window with the most findings`() {
        // bar 1: one, bar 2: none, bar 3: two, bar 4: one -> bars 3-4 hold three.
        val run = waitRun(wrong(0), wrong(8), ChordIncomplete(9, 9, setOf(c4)), wrong(12))
        assertEquals(BarRange(3, 4), SpotFinder.worst(passage(4), run))
    }

    @Test
    fun `a tie goes to the earliest window`() {
        val run = waitRun(wrong(0), wrong(12))
        assertEquals(BarRange(1, 2), SpotFinder.worst(passage(4), run))
    }

    @Test
    fun `no findings gives null`() {
        val run = waitRun(StepCorrect(0, 0), PassageComplete(1, 3))
        assertNull(SpotFinder.worst(passage(4), run))
    }

    @Test
    fun `a one-bar passage gives bar 1`() {
        assertEquals(BarRange(1, 1), SpotFinder.worst(passage(1), waitRun(wrong(2))))
    }

    @Test
    fun `timed findings are counted at their score note's bar`() {
        // Three bars; the learner skips the notes of steps 9 and 10 (bar 3) and plays the rest on time.
        val notes =
            (0 until 12).filter { it != 9 && it != 10 }.map {
                val down = it * NANOS_PER_BEAT
                PlayedNote(c4, 80, down, down + NANOS_PER_BEAT / 2, down + NANOS_PER_BEAT / 2)
            }
        val run = attempt(emptyList(), TEST_TEMPO_BPM, notes)
        assertEquals(BarRange(2, 3), SpotFinder.worst(passage(3), run))
    }

    @Test
    fun `timed findings are judged with the latency offset removed`() {
        val late = 200_000_000L
        val notes =
            (0 until 8).map {
                val down = it * NANOS_PER_BEAT + late
                PlayedNote(c4, 80, down, down + NANOS_PER_BEAT / 2, down + NANOS_PER_BEAT / 2)
            }
        assertNull(SpotFinder.worst(passage(2), attempt(emptyList(), TEST_TEMPO_BPM, notes, latency = late)))
    }
}
