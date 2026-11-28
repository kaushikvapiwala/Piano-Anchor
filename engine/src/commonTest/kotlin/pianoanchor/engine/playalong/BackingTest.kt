package pianoanchor.engine.playalong

import kotlinx.datetime.LocalDate
import pianoanchor.core.model.ChordMark
import pianoanchor.core.model.ChordQuality
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.Rights
import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.Tunables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackingTest {
    private val rights = Rights(RightsStatus.ORIGINAL, "test", "test", LocalDate(2026, 1, 1))

    /** [bars] bars of 4/4, one whole note in each. */
    private fun passage(
        vararg chords: ChordMark,
        bars: Int = 1,
    ) = Passage(
        id = "builtin:test",
        title = "Test",
        steps = List(bars) { Step(it * 4 * TICKS_PER_QUARTER, setOf(ScoreNote(MidiNote(72), 4 * TICKS_PER_QUARTER))) },
        timeSignature = TimeSignature(4, 4),
        tempoBpm = 60,
        rights = rights,
        chords = chords.toList(),
    )

    private val bar = 4 * TICKS_PER_QUARTER

    @Test
    fun `a C major chord over one bar is C3 E3 G3 for four seconds at 60 bpm`() {
        val tones = Backing.tones(passage(ChordMark(0, 0, ChordQuality.MAJOR)), 60)

        assertEquals(listOf(48, 52, 55), tones.map { it.note.number })
        assertTrue(tones.all { it.startMs == 0L && it.lengthMs == 4000L })
        assertTrue(tones.all { it.velocity == Tunables.BACKING_VELOCITY })
    }

    @Test
    fun `a G7 is G3 B3 D4 F4`() {
        val tones = Backing.tones(passage(ChordMark(0, 7, ChordQuality.DOMINANT_7)), 60)

        assertEquals(listOf(55, 59, 62, 65), tones.map { it.note.number })
    }

    @Test
    fun `a minor chord has a minor third`() {
        val tones = Backing.tones(passage(ChordMark(0, 9, ChordQuality.MINOR)), 60)

        assertEquals(listOf(57, 60, 64), tones.map { it.note.number })
    }

    @Test
    fun `two chords split at their onsets`() {
        val chords = arrayOf(ChordMark(0, 0, ChordQuality.MAJOR), ChordMark(bar / 2, 5, ChordQuality.MAJOR))
        val tones = Backing.tones(passage(*chords), 60)

        assertEquals(listOf(0L, 0L, 0L, 2000L, 2000L, 2000L), tones.map { it.startMs })
        assertEquals(List(6) { 2000L }, tones.map { it.lengthMs })
        assertEquals(listOf(48, 52, 55, 53, 57, 60), tones.map { it.note.number })
    }

    @Test
    fun `the last chord sounds until the end of the passage`() {
        val chords = arrayOf(ChordMark(0, 0, ChordQuality.MAJOR), ChordMark(bar, 7, ChordQuality.MAJOR))
        val tones = Backing.tones(passage(*chords, bars = 2), 60)

        assertEquals(listOf(4000L), tones.map { it.startMs }.distinct().drop(1))
        assertEquals(4000L, tones.last().lengthMs)
    }

    @Test
    fun `no chords give no tones`() {
        assertTrue(Backing.tones(passage(), 60).isEmpty())
    }

    @Test
    fun `double the tempo halves the lengths`() {
        val tones = Backing.tones(passage(ChordMark(0, 0, ChordQuality.MAJOR)), 120)

        assertTrue(tones.all { it.lengthMs == 2000L })
    }

    @Test
    fun `a chord at the end of the passage sounds nothing`() {
        val chords = arrayOf(ChordMark(0, 0, ChordQuality.MAJOR), ChordMark(bar, 7, ChordQuality.MAJOR))

        assertEquals(3, Backing.tones(passage(*chords), 60).size)
    }
}
