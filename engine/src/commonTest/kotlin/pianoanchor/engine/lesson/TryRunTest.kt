package pianoanchor.engine.lesson

import pianoanchor.core.model.TryCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TryRunTest {
    private fun TryRun.press(
        note: Int,
        velocity: Int = 80,
        at: Long = 0,
    ) = onNoteOn(note, velocity, at)

    @Test
    fun `any key finishes on the first press and then ignores events`() {
        val run = TryRun(TryCheck.AnyKey)
        assertEquals(TryProgress(0, 1, null, null, false, 0), run.progress)
        val done = run.press(30)
        assertEquals(TryProgress(1, 1, null, null, true, 0), done.progress)
        assertSame(done, done.press(31))
        assertSame(done, done.onNoteOff(30, 10))
    }

    @Test
    fun `play key reports the wrong key against the expected one then finishes`() {
        val wrong = TryRun(TryCheck.PlayKey(60, lit = true)).press(62)
        assertEquals(TryProgress(0, 1, 60, 62, false, 1), wrong.progress)
        assertEquals(TryProgress(1, 1, null, null, true, 1), wrong.press(60).progress)
    }

    @Test
    fun `note off does nothing outside a hold`() {
        val run = TryRun(TryCheck.PlayKey(60, lit = true))
        assertSame(run, run.onNoteOff(60, 5))
    }

    @Test
    fun `find all counts each key of the pitch class once`() {
        val run = TryRun(TryCheck.FindAll(0, 21..108))
        assertEquals(8, run.progress.total)
        assertEquals(24, run.progress.expected)
        val two = run.press(60).press(60).press(72)
        assertEquals(2, two.progress.done)
        val wrong = two.press(61)
        assertEquals(TryProgress(2, 8, 48, 61, false, 1), wrong.progress)
        assertEquals(2, wrong.press(61).progress.done)
    }

    @Test
    fun `find all breaks a tie towards the lower key and ignores its letter outside the range`() {
        val run = TryRun(TryCheck.FindAll(0, 48..72)).press(60)
        // 54 is 6 from 48 and 6 from 60 (found), 72 is 18 away: the lower key wins a tie of 48 and 60.
        assertEquals(48, run.press(54).progress.expected)
        // C0 is the right pitch class but outside the range: neither found nor wrong.
        assertEquals(run.progress, run.press(12).progress)
        assertEquals(run.progress, run.press(84).progress)
        // A key of another pitch class outside the range is still wrong.
        assertEquals(1, run.press(13).progress.wrongCount)
    }

    @Test
    fun `find all finishes with the last key`() {
        val run = TryRun(TryCheck.FindAll(0, 48..72)).press(48).press(60).press(72)
        assertTrue(run.progress.finished)
        assertEquals(3, run.progress.done)
    }

    @Test
    fun `sequence expects the next note and waits on a wrong one`() {
        var run = TryRun(TryCheck.Sequence(listOf(64, 67)))
        assertEquals(64, run.progress.expected)
        run = run.press(60)
        assertEquals(TryProgress(0, 2, 64, 60, false, 1), run.progress)
        run = run.press(64)
        assertEquals(TryProgress(1, 2, 67, null, false, 1), run.progress)
        assertEquals(TryProgress(2, 2, null, null, true, 1), run.press(67).progress)
    }

    @Test
    fun `repeat counts eight presses whatever the timing`() {
        var run = TryRun(TryCheck.Repeat(60, 8, 60))
        run = run.press(61)
        assertEquals(TryProgress(0, 8, 60, 61, false, 1), run.progress)
        repeat(7) { run = run.press(60, at = it * 37L) }
        assertFalse(run.progress.finished)
        run = run.press(60, at = 9000)
        assertEquals(TryProgress(8, 8, null, null, true, 1), run.progress)
    }

    @Test
    fun `hold finishes when kept long enough`() {
        val run = TryRun(TryCheck.Hold(60, 2, 60)).press(60, at = 1000)
        assertEquals(TryProgress(0, 1, 60, null, false, 0), run.progress)
        assertSame(run, run.onNoteOff(62, 2000))
        assertEquals(TryProgress(1, 1, null, null, true, 0), run.onNoteOff(60, 2700).progress)
    }

    @Test
    fun `hold released early resets and counts`() {
        val run = TryRun(TryCheck.Hold(60, 2, 60))
        val early = run.press(60, at = 0).onNoteOff(60, 1600)
        assertEquals(TryProgress(0, 1, 60, null, false, 1), early.progress)
        val again = early.press(60, at = 5000).onNoteOff(60, 6000)
        assertEquals(2, again.progress.wrongCount)
        assertSame(run, run.onNoteOff(60, 100))
    }

    @Test
    fun `hold treats another key as wrong and ignores events after finishing`() {
        val run = TryRun(TryCheck.Hold(60, 1, 60))
        assertEquals(62, run.press(62).progress.lastWrong)
        val done = run.press(60).onNoteOff(60, 2000)
        assertTrue(done.progress.finished)
        assertSame(done, done.onNoteOff(60, 3000))
    }

    @Test
    fun `soft then loud needs a soft press then a loud one`() {
        val run = TryRun(TryCheck.SoftThenLoud(60))
        val soft = run.press(60, 40)
        assertEquals(TryProgress(1, 2, 60, null, false, 0), soft.progress)
        assertEquals(TryProgress(2, 2, null, null, true, 0), soft.press(60, 100).progress)
    }

    @Test
    fun `soft then loud does not count a press at the wrong level`() {
        val run = TryRun(TryCheck.SoftThenLoud(60))
        val loudFirst = run.press(60, 100)
        assertEquals(TryProgress(0, 2, 60, null, false, 1), loudFirst.progress)
        val softAgain = loudFirst.press(60, 40).press(60, 70)
        assertEquals(TryProgress(1, 2, 60, null, false, 2), softAgain.progress)
        assertEquals(62, run.press(62).progress.lastWrong)
    }

    private fun stress(vararg velocities: Int): TryRun =
        velocities.fold(TryRun(TryCheck.StressBeatOne(60, 2, 60))) { run, v -> run.press(60, v) }

    @Test
    fun `stress beat one passes with accents on beat one`() {
        val run = stress(100, 70, 70, 70, 100, 70, 70, 70)
        assertEquals(TryProgress(8, 8, null, null, true, 0), run.progress)
    }

    @Test
    fun `stress beat one resets on flat playing and ignores other keys`() {
        val partial = stress(80, 80, 80)
        assertEquals(3, partial.progress.done)
        val wrong = partial.press(61)
        assertEquals(TryProgress(3, 8, 60, 61, false, 1), wrong.progress)
        val flat = stress(80, 80, 80, 80, 80, 80, 80, 80)
        assertEquals(TryProgress(0, 8, 60, null, false, 1), flat.progress)
    }

    @Test
    fun `chord finishes when all notes land in the window`() {
        val run = TryRun(TryCheck.Chord(setOf(60, 64, 67)))
        val two = run.press(60, at = 1000).press(64, at = 1050)
        assertEquals(TryProgress(2, 3, null, null, false, 0), two.progress)
        assertEquals(2, two.press(64, at = 1060).progress.done)
        assertEquals(TryProgress(3, 3, null, null, true, 0), two.press(67, at = 1090).progress)
    }

    @Test
    fun `chord resets when a note is late and starts a new cluster`() {
        val run = TryRun(TryCheck.Chord(setOf(60, 64, 67))).press(60, at = 0).press(64, at = 50)
        val late = run.press(67, at = 150)
        assertEquals(TryProgress(1, 3, null, null, false, 1), late.progress)
        assertEquals(TryProgress(3, 3, null, null, true, 1), late.press(60, at = 160).press(64, at = 170).progress)
    }

    @Test
    fun `chord resets on a stray note`() {
        val run = TryRun(TryCheck.Chord(setOf(60, 64, 67))).press(60, at = 0)
        assertEquals(TryProgress(0, 3, null, 62, false, 1), run.press(62, at = 10).progress)
        assertEquals(TryProgress(0, 1, null, 62, false, 1), TryRun(TryCheck.Chord(setOf(60))).press(62).progress)
    }

    private val eighths = TryCheck.Rhythm(60, List(8) { 480 }, 60)
    private val dotted = TryCheck.Rhythm(60, listOf(1440, 480, 1920), 60)

    private fun TryRun.tapAt(vararg times: Long): TryRun = times.fold(this) { run, at -> run.press(60, at = at) }

    @Test
    fun `rhythm finishes on even eighths at 60 bpm`() {
        val start = TryRun(eighths)
        assertEquals(TryProgress(0, 8, 60, null, false, 0, null), start.progress)
        val half = start.tapAt(0, 500, 1000, 1500)
        assertEquals(TryProgress(4, 8, 60, null, false, 0, null), half.progress)
        val done = half.tapAt(2000, 2500, 3000, 3500)
        assertEquals(TryProgress(8, 8, null, null, true, 0, null), done.progress)
    }

    @Test
    fun `rhythm taken one tap per click resets as late and starts again from that tap`() {
        val late = TryRun(eighths).tapAt(0, 500, 1500)
        assertEquals(TryProgress(1, 8, 60, null, false, 1, TryTiming.LATE), late.progress)
        assertEquals(TryProgress(2, 8, 60, null, false, 1, null), late.tapAt(2000).progress)
    }

    @Test
    fun `rhythm tolerance is a third of an eighth each side`() {
        val early = TryRun(eighths).tapAt(0, 320)
        assertEquals(TryProgress(1, 8, 60, null, false, 1, TryTiming.EARLY), early.progress)
        assertEquals(2, TryRun(eighths).tapAt(0, 330).progress.done)
        assertEquals(2, TryRun(eighths).tapAt(0, 670).progress.done)
        assertEquals(TryTiming.LATE, TryRun(eighths).tapAt(0, 680).progress.timing)
    }

    @Test
    fun `rhythm played even instead of dotted resets`() {
        val ok = TryRun(dotted).tapAt(0, 1500, 2000)
        assertEquals(TryProgress(3, 3, null, null, true, 0, null), ok.progress)
        val even = TryRun(dotted).tapAt(0, 1000)
        assertEquals(TryProgress(1, 3, 60, null, false, 1, TryTiming.EARLY), even.progress)
        assertEquals(TryTiming.EARLY, TryRun(dotted).tapAt(0, 1500, 1700).progress.timing)
        assertEquals(TryTiming.LATE, TryRun(dotted).tapAt(0, 1500, 2500).progress.timing)
        assertEquals(TryTiming.LATE, TryRun(dotted).tapAt(0, 1500, 2400).progress.timing)
    }

    @Test
    fun `rhythm scales with the tempo and ignores the last note's length`() {
        val fast = TryRun(TryCheck.Rhythm(60, listOf(960, 960, 1920), 120))
        assertEquals(3, fast.tapAt(0, 500, 1000).progress.done)
        assertEquals(TryTiming.LATE, fast.tapAt(0, 600).progress.timing)
    }

    @Test
    fun `rhythm treats another key as wrong and keeps the count`() {
        val run = TryRun(eighths).tapAt(0, 500).press(62, at = 700)
        assertEquals(TryProgress(2, 8, 60, 62, false, 1, null), run.progress)
        assertEquals(TryProgress(3, 8, 60, null, false, 1, null), run.press(60, at = 1000).progress)
    }

    @Test
    fun `a wrong key clears the timing of the reset before it`() {
        val run = TryRun(eighths).tapAt(0, 100).press(62, at = 200)
        assertNull(run.progress.timing)
    }

    @Test
    fun `rhythm ignores note offs and a finished run`() {
        val run = TryRun(TryCheck.Rhythm(60, listOf(480, 480), 60)).tapAt(0)
        assertSame(run, run.onNoteOff(60, 100))
        val done = run.tapAt(500)
        assertTrue(done.progress.finished)
        assertSame(done, done.press(60, at = 9000))
    }

    @Test
    fun `passage checks are not evaluated here`() {
        assertFailsWith<IllegalArgumentException> { TryRun(TryCheck.Passage("hot-cross-buns")) }
    }

    @Test
    fun `progress has no expected key once finished`() {
        assertNull(TryRun(TryCheck.Sequence(listOf(60))).press(60).progress.expected)
    }
}
