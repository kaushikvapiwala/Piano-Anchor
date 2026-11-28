package pianoanchor.engine.guidance

import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.BarRange
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Strictness
import pianoanchor.core.model.WrongNote
import pianoanchor.engine.profile
import pianoanchor.testing.TEST_TEMPO_BPM
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class WeakSpotTest {
    private val c4 = MidiNote(60)
    private val passage = passageOf(*Array(16) { setOf(c4) })
    private val strictness = Strictness.STANDARD.profile()

    private fun attempt(
        tempo: Int? = null,
        played: List<PlayedNote> = emptyList(),
        band: Band = Band.GOOD,
        mode: RunMode = RunMode.PRACTICE,
        passageId: String = "test:passage",
        mistakesAtStep: Int? = 12,
    ) = Attempt(
        id = "a",
        passageId = passageId,
        deviceId = "d",
        startedAt = Instant.fromEpochSeconds(0),
        durationNanos = 1,
        stepCount = 1,
        wrongNotes = 0,
        incompleteChords = 0,
        accuracy = 0.5,
        band = band,
        events = listOfNotNull(mistakesAtStep?.let { WrongNote(1, it, MidiNote(61)) }),
        tempoBpm = tempo,
        playedNotes = played,
        mode = mode,
    )

    @Test
    fun `an older wait run has no analysis`() {
        assertNull(WeakSpot.analyse(passage, attempt(), strictness))
    }

    @Test
    fun `a timed run and a wait run with played notes are analysed`() {
        val note = PlayedNote(c4, 80, 0, 100_000_000, 100_000_000)
        assertNotNull(WeakSpot.analyse(passage, attempt(tempo = TEST_TEMPO_BPM), strictness))
        assertNotNull(WeakSpot.analyse(passage, attempt(played = listOf(note)), strictness))
    }

    @Test
    fun `a practice run's worst bars come from its wrong notes`() {
        assertEquals(BarRange(3, 4), WeakSpot.of(passage, attempt(), strictness))
        assertEquals(BarRange(3, 4), WeakSpot.find(passage, attempt(), emptyList()))
    }

    @Test
    fun `a timed run's bars come from its analysed findings`() {
        // No notes were played, so the analysis finds the missing notes.
        assertNotNull(WeakSpot.of(passage, attempt(tempo = TEST_TEMPO_BPM), strictness))
    }

    @Test
    fun `a perform take, a clean run and a sight-reading exercise offer none`() {
        assertNull(WeakSpot.of(passage, attempt(mode = RunMode.PERFORM), strictness))
        assertNull(WeakSpot.of(passage, attempt(band = Band.CLEAN), strictness))
        assertNull(WeakSpot.of(passage, attempt(passageId = "sight:1:7"), strictness))
    }

    @Test
    fun `a run with no mistakes offers none`() {
        assertNull(WeakSpot.of(passage, attempt(mistakesAtStep = null), strictness))
    }
}
