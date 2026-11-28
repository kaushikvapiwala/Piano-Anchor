package pianoanchor.engine.lesson

import pianoanchor.core.model.TryCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TryRunEvenSequenceTest {
    private fun TryRun.press(
        note: Int,
        at: Long,
    ) = onNoteOn(note, 80, at)

    private val even = TryCheck.EvenSequence(listOf(60, 62, 64), 60)

    private fun TryRun.notesAt(vararg presses: Pair<Int, Long>): TryRun =
        presses.fold(this) { run, (note, at) -> run.press(note, at) }

    @Test
    fun `even sequence finishes on notes one beat apart`() {
        val start = TryRun(even)
        assertEquals(TryProgress(0, 3, 60, null, false, 0, null), start.progress)
        val half = start.notesAt(60 to 0, 62 to 1000)
        assertEquals(TryProgress(2, 3, 64, null, false, 0, null), half.progress)
        assertEquals(TryProgress(3, 3, null, null, true, 0, null), half.notesAt(64 to 2000).progress)
    }

    @Test
    fun `even sequence starts at any moment`() {
        assertEquals(1, TryRun(even).notesAt(60 to 123_456).progress.done)
    }

    @Test
    fun `even sequence taken too fast restarts and a press that is not the first note expects the first`() {
        val run = TryRun(even).notesAt(60 to 0, 62 to 1000, 64 to 1700)
        assertEquals(TryProgress(0, 3, 60, null, false, 1, TryTiming.EARLY), run.progress)
    }

    @Test
    fun `even sequence timing miss on the first note counts it as note one`() {
        val run = TryRun(TryCheck.EvenSequence(listOf(60, 62, 60), 60)).notesAt(60 to 0, 62 to 1000, 60 to 3000)
        assertEquals(TryProgress(1, 3, 62, null, false, 1, TryTiming.LATE), run.progress)
    }

    @Test
    fun `even sequence wrong key keeps the count`() {
        val run = TryRun(even).notesAt(60 to 0, 63 to 1000)
        assertEquals(TryProgress(1, 3, 62, 63, false, 1, null), run.progress)
    }

    @Test
    fun `even sequence steady gaps a little fast pass press two and fail press three on drift`() {
        val run = TryRun(even).notesAt(60 to 0, 62 to 850)
        assertEquals(2, run.progress.done)
        val failed = run.notesAt(64 to 1700)
        assertEquals(TryProgress(0, 3, 60, null, false, 1, TryTiming.EARLY), failed.progress)
    }

    @Test
    fun `even sequence ignores note offs`() {
        val run = TryRun(even).notesAt(60 to 0)
        assertSame(run, run.onNoteOff(60, 100))
    }
}
