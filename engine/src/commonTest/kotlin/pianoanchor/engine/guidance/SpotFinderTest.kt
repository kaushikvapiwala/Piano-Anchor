package pianoanchor.engine.guidance

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.ChordIncomplete
import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Finding
import pianoanchor.core.model.MatchEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PassageComplete
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.StepCorrect
import pianoanchor.core.model.WrongNote
import pianoanchor.testing.TEST_TEMPO_BPM
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class SpotFinderTest {
    private val c4 = MidiNote(60)

    /** [bars] bars of 4/4 quarter notes, every step a C4. */
    private fun passage(bars: Int): Passage = passageOf(*Array(bars * 4) { setOf(c4) })

    private fun waitRun(vararg events: MatchEvent) = attempt(events.toList(), tempo = null)

    private fun attempt(
        events: List<MatchEvent>,
        tempo: Int?,
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
        playedNotes = emptyList(),
    )

    private fun wrong(step: Int) = WrongNote(step.toLong(), step, MidiNote(61))

    @Test
    fun `picks the two-bar window with the most findings`() {
        // bar 1: one, bar 2: none, bar 3: two, bar 4: one -> bars 3-4 hold three.
        val run = waitRun(wrong(0), wrong(8), ChordIncomplete(9, 9, setOf(c4)), wrong(12))
        assertEquals(BarRange(3, 4), SpotFinder.worst(passage(4), run, emptyList()))
    }

    @Test
    fun `a tie goes to the earliest window`() {
        val run = waitRun(wrong(0), wrong(12))
        assertEquals(BarRange(1, 2), SpotFinder.worst(passage(4), run, emptyList()))
    }

    @Test
    fun `no findings gives null`() {
        val run = waitRun(StepCorrect(0, 0), PassageComplete(1, 3))
        assertNull(SpotFinder.worst(passage(4), run, emptyList()))
    }

    @Test
    fun `a one-bar passage gives bar 1`() {
        assertEquals(BarRange(1, 1), SpotFinder.worst(passage(1), waitRun(wrong(2)), emptyList()))
    }

    @Test
    fun `timed findings are counted at their score note's bar`() {
        // Three bars; two notes of bar 3 are missed, one of bar 1.
        val findings = listOf(0, 9, 10).map { Finding(ErrorKind.MISSED_NOTE, ScoreNoteRef(it, c4), played = null) }
        val run = attempt(emptyList(), TEST_TEMPO_BPM)
        assertEquals(BarRange(2, 3), SpotFinder.worst(passage(3), run, findings))
    }

    @Test
    fun `a timed run without findings gives null`() {
        assertNull(SpotFinder.worst(passage(2), attempt(emptyList(), TEST_TEMPO_BPM), emptyList()))
    }

    @Test
    fun `a wait-mode run ignores the timed findings`() {
        val findings = listOf(Finding(ErrorKind.MISSED_NOTE, ScoreNoteRef(0, c4), played = null))
        assertNull(SpotFinder.worst(passage(2), waitRun(StepCorrect(0, 0)), findings))
    }
}
