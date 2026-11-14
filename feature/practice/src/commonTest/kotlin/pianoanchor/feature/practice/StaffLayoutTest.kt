package pianoanchor.feature.practice

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val Q = TICKS_PER_QUARTER
private const val WIDE = 875f
private const val TOLERANCE = 0.01f

class StaffLayoutTest {
    private val c4 = MidiNote(60)
    private val e4 = MidiNote(64)
    private val g4 = MidiNote(67)

    private fun passage(
        vararg steps: Step,
        time: TimeSignature = TimeSignature(4, 4),
    ) = Passage("test", "Test", steps.toList(), time, tempoBpm = 100)

    private fun step(
        onset: Int,
        length: Int = Q,
        vararg pitches: MidiNote = arrayOf(c4),
    ) = Step(onset, pitches.mapTo(LinkedHashSet()) { ScoreNote(it, length) })

    /** [count] quarters in a row from tick 0. */
    private fun quarters(count: Int) = (0 until count).map { step(it * Q) }.toTypedArray()

    private fun assertNear(
        expected: Float,
        actual: Float,
    ) = assertTrue(abs(expected - actual) < TOLERANCE, "expected $expected, was $actual")

    private fun StaffLayout.gaps() = noteX.zipWithNext { a, b -> b - a }

    @Test
    fun `x is proportional to onset ticks`() {
        val layout = staffLayout(passage(step(0), step(Q), step(3 * Q)), WIDE)

        val (quarter, half) = layout.gaps()
        assertNear(2 * quarter, half)
        assertNear(quarter, layout.finalBarX - layout.noteX[2])
    }

    @Test
    fun `notes start after the time signature and the final bar fits the width`() {
        val layout = staffLayout(passage(*quarters(4)), WIDE)

        assertTrue(layout.noteX.first() > 96f, "first note at ${layout.noteX.first()}")
        assertTrue(layout.finalBarX <= WIDE - 24f, "final bar at ${layout.finalBarX}")
    }

    @Test
    fun `minimum gap is kept`() {
        // A sixteenth among whole notes would get under 28 dp if spaced purely by ticks.
        val layout = staffLayout(passage(step(0, 4 * Q), step(4 * Q, Q / 4), step(4 * Q + Q / 4, 4 * Q)), WIDE)

        val (whole, sixteenth) = layout.gaps()
        assertNear(28f, sixteenth)
        assertTrue(whole > 28f)
        assertTrue(layout.finalBarX <= WIDE - 24f)
    }

    @Test
    fun `crowded passage compresses evenly`() {
        val layout = staffLayout(passage(*quarters(40)), WIDE)

        val gaps = layout.gaps()
        gaps.forEach { assertNear(gaps.first(), it) }
        assertTrue(gaps.first() < 28f)
        assertNear(WIDE - 24f, layout.finalBarX)
    }

    @Test
    fun `bar lines at bar boundaries for 4 4`() {
        val layout = staffLayout(passage(*quarters(12)), WIDE)

        assertEquals(2, layout.barLineX.size)
        assertTrue(layout.barLineX[0] in layout.noteX[3]..layout.noteX[4])
        assertTrue(layout.barLineX[1] in layout.noteX[7]..layout.noteX[8])
    }

    @Test
    fun `bar lines at bar boundaries for 3 4`() {
        val layout = staffLayout(passage(*quarters(9), time = TimeSignature(3, 4)), WIDE)

        assertEquals(2, layout.barLineX.size)
        assertTrue(layout.barLineX[0] in layout.noteX[2]..layout.noteX[3])
        assertTrue(layout.barLineX[1] in layout.noteX[5]..layout.noteX[6])
    }

    @Test
    fun `6 8 bar is three quarters`() {
        val layout = staffLayout(passage(*quarters(6), time = TimeSignature(6, 8)), WIDE)

        assertEquals(1, layout.barLineX.size)
        assertTrue(layout.barLineX[0] in layout.noteX[2]..layout.noteX[3])
    }

    @Test
    fun `bar line inside a held note sits at its tick`() {
        // A whole note from beat 3 of bar 1 to beat 3 of bar 2: the bar line falls inside it.
        val layout = staffLayout(passage(step(0, 2 * Q), step(2 * Q, 4 * Q), step(6 * Q, 2 * Q)), WIDE)

        val bar = layout.barLineX.single()
        assertNear((layout.noteX[1] + layout.noteX[2]) / 2, bar)
    }

    @Test
    fun `bar line before a note keeps clear of it`() {
        val layout = staffLayout(passage(*quarters(8)), WIDE)

        val bar = layout.barLineX.single()
        assertNear(30f, layout.noteX[4] - bar)
    }

    @Test
    fun `bar line before an opening note after a rest bar keeps off it`() {
        val layout = staffLayout(passage(step(4 * Q), step(5 * Q)), WIDE)

        assertTrue(layout.barLineX.single() < layout.noteX[0] - 10f)
    }

    @Test
    fun `narrow staff never draws right to left`() {
        val layout = staffLayout(passage(*quarters(3)), 100f)

        assertTrue(layout.noteX.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(layout.finalBarX >= layout.noteX.last())
    }

    @Test
    fun `no inner bar line for a one-bar passage`() {
        val layout = staffLayout(passage(*quarters(4)), WIDE)

        assertTrue(layout.barLineX.isEmpty())
    }

    @Test
    fun `final bar after the last note's end`() {
        // The last note is a whole note: the final bar sits a whole note's width after it, not at its onset.
        val layout = staffLayout(passage(step(0), step(Q, 4 * Q)), WIDE)

        assertNear(4 * (layout.noteX[1] - layout.noteX[0]), layout.finalBarX - layout.noteX[1])
    }

    @Test
    fun `final bar follows a long chord note in an earlier step`() {
        val layout = staffLayout(passage(step(0, 4 * Q, c4, e4), step(Q, Q, g4)), WIDE)

        assertNear(3 * (layout.noteX[1] - layout.noteX[0]), layout.finalBarX - layout.noteX[1])
    }

    @Test
    fun `half and whole notes are hollow, quarters filled`() {
        val layout =
            staffLayout(
                passage(
                    step(0, Q),
                    step(Q, 2 * Q),
                    step(3 * Q, 4 * Q),
                    Step(
                        7 * Q,
                        setOf(
                            ScoreNote(c4, Q),
                            ScoreNote(
                                g4,
                                2 * Q,
                            ),
                        ),
                    ),
                ),
                WIDE,
            )

        assertEquals(listOf(emptySet(), setOf(c4), setOf(c4), setOf(g4)), layout.hollow)
    }
}
