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
    fun `find all of F sharp finishes after the seven F sharps and calls an F wrong`() {
        val check = TryCheck.FindAll(6, 21..108)
        val run = TryRun(check)
        assertEquals(7, run.progress.total)
        val wrong = run.press(65)
        assertEquals(1, wrong.progress.wrongCount)
        assertEquals(66, wrong.progress.expected)
        val done = listOf(30, 42, 54, 66, 78, 90, 102).fold(run) { r, note -> r.press(note) }
        assertTrue(done.progress.finished)
        assertEquals(7, done.progress.done)
    }

    @Test
    fun `the D7 shortcut chord finishes in a cluster and G B D fails it`() {
        val d7 = TryRun(TryCheck.Chord(setOf(42, 48, 50)))
        assertTrue(
            d7
                .press(42, at = 0)
                .press(48, at = 30)
                .press(50, at = 60)
                .progress.finished,
        )
        val g = d7.press(43, at = 0).press(47, at = 30).press(50, at = 60)
        assertFalse(g.progress.finished)
        assertTrue(g.progress.wrongCount > 0)
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
    fun `rhythm taps a fifth fast fail on drift as early`() {
        val run = TryRun(eighths).tapAt(0, 400, 800)
        assertEquals(TryProgress(1, 8, 60, null, false, 1, TryTiming.EARLY), run.progress)
    }

    @Test
    fun `rhythm taps a fifth slow fail on drift as late`() {
        val run = TryRun(eighths).tapAt(0, 600, 1200)
        assertEquals(TryProgress(1, 8, 60, null, false, 1, TryTiming.LATE), run.progress)
    }

    @Test
    fun `rhythm steady tempo a third slow passes press two and fails press three on drift`() {
        assertEquals(2, TryRun(eighths).tapAt(0, 667).progress.done)
        val run = TryRun(eighths).tapAt(0, 667, 1334)
        assertEquals(TryProgress(1, 8, 60, null, false, 1, TryTiming.LATE), run.progress)
    }

    @Test
    fun `rhythm jitter that cancels still finishes`() {
        val done = TryRun(eighths).tapAt(0, 520, 1000, 1520, 2000, 2520, 3000, 3520)
        assertEquals(TryProgress(8, 8, null, null, true, 0, null), done.progress)
    }

    @Test
    fun `rhythm timing comes from the gap when the gap fails and from the drift when only drift fails`() {
        // Gap fine (+170) but drift +340 outside: LATE from drift.
        val drift = TryRun(eighths).tapAt(0, 670, 1340)
        assertEquals(TryTiming.LATE, drift.progress.timing)
        // Gap early (-200) and drift -30 (inside): the gap decides, EARLY.
        val gap = TryRun(eighths).tapAt(0, 670, 970)
        assertEquals(TryTiming.EARLY, gap.progress.timing)
        // Gap late (+200), drift +30: the gap decides, LATE.
        assertEquals(TryTiming.LATE, TryRun(eighths).tapAt(0, 330, 1030).progress.timing)
    }

    @Test
    fun `rhythm restarts the drift from the failing tap`() {
        val run = TryRun(eighths).tapAt(0, 400, 800, 1300)
        assertEquals(TryProgress(2, 8, 60, null, false, 1, null), run.progress)
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

    private fun TryRun.off(
        note: Int,
        at: Long,
    ) = onNoteOff(note, at)

    private fun TryRun.pedal(
        value: Int,
        at: Long,
    ) = onPedal(value, at)

    private fun TryRun.chord(
        notes: Set<Int>,
        at: Long,
    ): TryRun = notes.foldIndexed(this) { i, run, note -> run.press(note, at = at + i * 10L) }

    private fun TryRun.release(
        notes: Set<Int>,
        at: Long,
    ): TryRun = notes.foldIndexed(this) { i, run, note -> run.off(note, at + i * 10L) }

    private val legato = TryCheck.Legato(listOf(60, 62, 64))

    @Test
    fun `legato finishes when each key goes down just after the last one comes up`() {
        val run =
            TryRun(legato)
                .press(60, at = 0)
                .off(60, 500)
                .press(62, at = 520)
                .off(62, 1000)
                .press(64, at = 1020)
        assertEquals(TryProgress(3, 3, null, null, true, 0), run.progress)
    }

    @Test
    fun `legato accepts a short overlap`() {
        val run =
            TryRun(legato)
                .press(60, at = 0)
                .press(62, at = 500)
                .off(60, 550)
                .off(62, 1000)
                .press(64, at = 1010)
        assertTrue(run.progress.finished)
        assertEquals(0, run.progress.wrongCount)
    }

    @Test
    fun `legato too long a gap is a GAP and starts again`() {
        val gap = TryRun(legato).press(60, at = 0).off(60, 500).press(62, at = 700)
        assertEquals(TryProgress(0, 3, 60, null, false, 1, TryTiming.GAP), gap.progress)
        assertEquals(1, gap.press(60, at = 800).progress.done)
    }

    @Test
    fun `legato a gap press of the first note counts as the start of a fresh attempt`() {
        val again = TryCheck.Legato(listOf(60, 62, 60))
        val run =
            TryRun(again)
                .press(60, at = 0)
                .off(60, 500)
                .press(62, at = 510)
                .off(62, 1000)
                .press(60, at = 1500)
        assertEquals(TryProgress(1, 3, 62, null, false, 1, TryTiming.GAP), run.progress)
        assertEquals(
            2,
            run
                .off(60, 1900)
                .press(62, at = 1910)
                .progress.done,
        )
    }

    @Test
    fun `legato held into the next note too long is an OVERLAP at the note off`() {
        val run = TryRun(legato).press(60, at = 0).press(62, at = 100).off(60, 400)
        assertEquals(TryProgress(0, 3, 60, null, false, 1, TryTiming.OVERLAP), run.progress)
        assertEquals(0, run.off(62, 500).progress.done)
    }

    @Test
    fun `legato a key still down at the press after next is an OVERLAP`() {
        val run =
            TryRun(legato)
                .press(60, at = 0)
                .press(62, at = 100)
                .off(62, 120)
                .press(64, at = 130)
        assertEquals(TryProgress(0, 3, 60, null, false, 1, TryTiming.OVERLAP), run.progress)
    }

    @Test
    fun `legato a wrong key waits and a stray release changes nothing`() {
        val wrong = TryRun(legato).press(60, at = 0).press(61, at = 10)
        assertEquals(TryProgress(1, 3, 62, 61, false, 1), wrong.progress)
        assertEquals(wrong.progress, wrong.off(99, 20).progress)
        assertEquals(TryRun(legato).progress, TryRun(legato).off(60, 5).progress)
    }

    @Test
    fun `legato a stray release while two keys are down changes nothing and the older key may come up late`() {
        val both = TryRun(legato).press(60, at = 0).press(62, at = 100)
        assertEquals(both.progress, both.off(99, 120).progress)
        val inOrder = both.off(62, 120).off(60, 130)
        assertEquals(TryProgress(2, 3, 64, null, false, 0), inOrder.progress)
    }

    private val d = setOf(48, 52, 55)

    @Test
    fun `pedal hold a release after the cluster window without the pedal fails the attempt`() {
        val run = TryRun(TryCheck.PedalHold(d)).press(48, at = 0).press(52, at = 10).off(48, 500)
        assertEquals(TryProgress(0, 3, null, null, false, 1, TryTiming.LATE, true), run.progress)
    }

    @Test
    fun `pedal hold a key lifted before the pedal is simply not held`() {
        val run = TryRun(TryCheck.PedalHold(d)).chord(d, 0).off(48, 20).pedal(127, 50)
        assertEquals(1, run.progress.done)
        val ringing = run.release(d - 48, 100)
        assertEquals(2, ringing.progress.done)
        assertEquals(2, ringing.off(48, 200).progress.done)
    }

    @Test
    fun `pedal hold finishes when the sound rings on for a second after the keys come up`() {
        val hold = TryCheck.PedalHold(d)
        val down = TryRun(hold).pedal(127, 0).chord(d, 100)
        assertEquals(TryProgress(1, 3, null, null, false, 0), down.progress)
        val up = down.release(d, 500)
        assertEquals(2, up.progress.done)
        val done = up.pedal(0, 1_800)
        assertEquals(TryProgress(3, 3, null, null, true, 0), done.progress)
        assertSame(done, done.pedal(127, 2_000))
        assertSame(done, done.off(48, 2_100))
    }

    @Test
    fun `pedal hold lifted too soon fails with EARLY`() {
        val run =
            TryRun(TryCheck.PedalHold(d))
                .pedal(127, 0)
                .chord(d, 100)
                .release(d, 500)
                .pedal(0, 1_000)
        assertEquals(TryProgress(0, 3, null, null, false, 1, TryTiming.EARLY), run.progress)
        val keysHeld = TryRun(TryCheck.PedalHold(d)).pedal(127, 0).chord(d, 100).pedal(0, 2_000)
        assertEquals(1, keysHeld.progress.wrongCount)
    }

    @Test
    fun `pedal hold chord long before the pedal does not meet part one`() {
        val run = TryRun(TryCheck.PedalHold(d)).chord(d, 0).pedal(127, 500)
        assertEquals(TryProgress(0, 3, null, null, false, 1, TryTiming.LATE, true), run.progress)
        assertEquals(0, run.release(d, 600).progress.done)
    }

    @Test
    fun `pedal hold pedal just after the first note still counts`() {
        val run = TryRun(TryCheck.PedalHold(d)).chord(d, 0).pedal(127, 50)
        assertEquals(1, run.progress.done)
        val tapped = TryRun(TryCheck.PedalHold(d)).chord(d, 0).release(d, 30).pedal(127, 60)
        assertEquals(2, tapped.progress.done)
    }

    @Test
    fun `pedal hold counts a half-pressed wobble as one press`() {
        val run =
            TryRun(TryCheck.PedalHold(d))
                .pedal(70, 0)
                .pedal(50, 10)
                .pedal(70, 20)
                .chord(d, 100)
                .release(d, 500)
        assertEquals(2, run.progress.done)
        assertEquals(2, run.pedal(40, 600).progress.done)
        assertEquals(1, run.pedal(10, 700).progress.wrongCount)
    }

    @Test
    fun `pedal hold a note outside the chord fails the attempt and a late note starts again`() {
        val wrong = TryRun(TryCheck.PedalHold(d)).pedal(127, 0).press(48, at = 10).press(60, at = 20)
        assertEquals(TryProgress(0, 3, null, 60, false, 1), wrong.progress)
        val late = TryRun(TryCheck.PedalHold(d)).pedal(127, 0).press(48, at = 10).press(52, at = 500)
        assertEquals(TryProgress(0, 3, null, null, false, 1, TryTiming.LATE, true), late.progress)
        assertEquals(
            1,
            late
                .press(55, at = 510)
                .press(48, at = 520)
                .progress.done,
        )
        assertEquals(
            0,
            TryRun(TryCheck.PedalHold(d))
                .pedal(127, 0)
                .pedal(0, 50)
                .progress.wrongCount,
        )
    }

    @Test
    fun `pedal hold pressing a chord key again while it rings goes back to part two`() {
        val run =
            TryRun(TryCheck.PedalHold(d))
                .pedal(127, 0)
                .chord(d, 100)
                .release(d, 500)
                .press(48, at = 600)
        assertEquals(1, run.progress.done)
        assertEquals(2, run.off(48, 700).progress.done)
        val held = TryRun(TryCheck.PedalHold(d)).pedal(127, 0).chord(d, 100).off(48, 200)
        assertEquals(1, held.progress.done)
    }

    private val c = setOf(48, 52, 55)
    private val f = setOf(53, 57, 60)
    private val g = setOf(55, 59, 62)
    private val change = TryCheck.PedalChange(listOf(c, f, g, c))

    private fun TryRun.next(
        notes: Set<Int>,
        at: Long,
        changeAfter: Long = 200,
    ): TryRun = chord(notes, at).pedal(0, at + changeAfter).pedal(127, at + changeAfter + 50)

    @Test
    fun `pedal change finishes when the pedal is changed after each chord`() {
        val first = TryRun(change).pedal(127, 0).chord(c, 100)
        assertEquals(TryProgress(1, 4, null, null, false, 0), first.progress)
        val run = first.next(f, 1_000).next(g, 2_000).next(c, 3_000)
        assertEquals(TryProgress(4, 4, null, null, true, 0), run.progress)
        assertSame(run, run.pedal(0, 4_000))
    }

    @Test
    fun `pedal change lifted before the new chord is EARLY`() {
        val run = TryRun(change).pedal(127, 0).chord(c, 100).pedal(0, 900)
        assertEquals(TryProgress(0, 4, null, null, false, 1, TryTiming.EARLY), run.progress)
    }

    @Test
    fun `pedal change with no change is LATE once the next chord or the window closes`() {
        val base = TryRun(change).pedal(127, 0).chord(c, 100).chord(f, 1_000)
        assertEquals(TryProgress(1, 4, null, null, false, 0), base.progress)
        val nextChord = base.press(59, at = 2_000)
        assertEquals(TryProgress(0, 4, null, null, false, 1, TryTiming.LATE), nextChord.progress)
        val slow = base.pedal(0, 1_100).pedal(127, 1_800)
        assertEquals(TryProgress(0, 4, null, null, false, 1, TryTiming.LATE), slow.progress)
        val sameNote = base.press(53, at = 1_700)
        assertEquals(1, sameNote.progress.wrongCount)
        assertEquals(TryProgress(1, 4, null, null, false, 0), base.press(53, at = 1_100).progress)
    }

    @Test
    fun `pedal change wrong note, late chord note and pedal before the chord`() {
        val wrong = TryRun(change).pedal(127, 0).press(40, at = 10)
        assertEquals(TryProgress(0, 4, null, 40, false, 1), wrong.progress)
        val late = TryRun(change).pedal(127, 0).press(48, at = 0).press(52, at = 500)
        assertEquals(TryProgress(0, 4, null, null, false, 1, TryTiming.LATE, true), late.progress)
        assertEquals(
            1,
            late
                .press(55, at = 510)
                .press(48, at = 520)
                .progress.wrongCount,
        )
        val other =
            TryRun(change)
                .pedal(127, 0)
                .chord(c, 0)
                .chord(f, 1_000)
                .press(53, at = 1_700)
        assertEquals(TryProgress(0, 4, null, null, false, 1, TryTiming.LATE), other.progress)
        val quiet = TryRun(change).pedal(127, 0).pedal(0, 50).pedal(127, 60)
        assertEquals(TryProgress(0, 4, null, null, false, 0), quiet.progress)
    }

    @Test
    fun `pedal change first chord may meet the pedal inside its cluster`() {
        val inside =
            TryRun(change)
                .press(48, at = 0)
                .pedal(127, 30)
                .press(52, at = 40)
                .press(55, at = 50)
        assertEquals(1, inside.progress.done)
        val after = TryRun(change).chord(c, 0).pedal(127, 60)
        assertEquals(1, after.progress.done)
        val tooLate = TryRun(change).chord(c, 0).pedal(127, 500)
        assertEquals(TryProgress(0, 4, null, null, false, 1, TryTiming.LATE, true), tooLate.progress)
    }

    @Test
    fun `the pedal is ignored by checks that do not read it`() {
        val plain = TryRun(TryCheck.Sequence(listOf(64, 67))).press(64, at = 0).press(67, at = 100)
        val noisy =
            TryRun(TryCheck.Sequence(listOf(64, 67)))
                .pedal(127, 0)
                .press(64, at = 10)
                .pedal(0, 50)
                .press(67, at = 100)
        assertEquals(plain.progress, noisy.progress)
    }

    @Test
    fun `legato the last note pressed while the one before is held finishes when it comes up in time`() {
        val held =
            TryRun(legato)
                .press(60, at = 0)
                .press(62, at = 500)
                .off(60, 510)
                .press(64, at = 1_000)
        assertEquals(
            TryProgress(3, 3, null, null, false, 0),
            held
                .off(62, 1_050)
                .also {
                    assertTrue(it.progress.finished)
                }.progress
                .copy(finished = false),
        )
        val late =
            TryRun(
                legato,
            ).press(60, at = 0).press(62, at = 500).off(60, 510).press(64, at = 1_000).off(62, 3_000)
        assertEquals(TryProgress(0, 3, 60, null, false, 1, TryTiming.OVERLAP), late.progress)
        val waiting =
            TryRun(legato)
                .press(60, at = 0)
                .press(62, at = 500)
                .off(60, 510)
                .press(64, at = 1_000)
        assertEquals(false, waiting.progress.finished)
        assertEquals(1, waiting.press(65, at = 1_010).progress.wrongCount)
    }

    @Test
    fun `a run seeded with the pedal down sees the first lift as a crossing`() {
        val run = TryRun(TryCheck.PedalHold(d), pedalDown = true).chord(d, 0)
        assertEquals(1, run.progress.done)
        val lifted =
            TryRun(
                TryCheck.PedalChange(listOf(d, setOf(53, 57, 60))),
                pedalDown = true,
            ).chord(d, 0).pedal(0, 100)
        assertEquals(TryTiming.EARLY, lifted.progress.timing)
        assertEquals(1, lifted.progress.wrongCount)
        assertSame(TryRun(TryCheck.PedalHold(d), pedalDown = true).let { it }.progress.done, 0)
    }
}
