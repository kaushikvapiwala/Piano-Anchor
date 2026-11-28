package pianoanchor.feature.practice

import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PedalSpan
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.core.model.TimeSignature
import pianoanchor.engine.content.NoteText
import pianoanchor.engine.notation.Engraving
import pianoanchor.engine.notation.Staff
import pianoanchor.testing.TEST_RIGHTS
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
    ) = Passage("test", "Test", steps.toList(), time, tempoBpm = 100, rights = TEST_RIGHTS)

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
        // The last note is a dotted half: the final bar sits its width after it, not at its onset.
        val layout = staffLayout(passage(step(0), step(Q, 3 * Q)), WIDE)

        assertNear(3 * (layout.noteX[1] - layout.noteX[0]), layout.finalBarX - layout.noteX[1])
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

        // The step with C4 (a quarter) and G4 (a half) is one chord of quarter value: both heads filled.
        assertEquals(listOf(emptySet(), setOf(c4), setOf(c4), emptySet()), layout.hollow)
    }

    private fun noteText(
        text: String,
        key: KeySignature = KeySignature.C,
    ) = passage(*NoteText.parse(text, TimeSignature(4, 4)).toTypedArray()).copy(keySignature = key)

    @Test
    fun `key signature width is reserved only when the key is not C`() {
        val c = staffLayout(noteText("C4w"), WIDE)
        val g = staffLayout(noteText("C4w", KeySignature.G), WIDE)
        val d = staffLayout(noteText("C4w", KeySignature(2)), WIDE)

        assertNear(0f, c.keyWidth)
        assertNear(KEY_SHARP_WIDTH_DP, g.keyWidth)
        assertNear(2 * KEY_SHARP_WIDTH_DP, d.keyWidth)
        assertNear(KEY_SHARP_WIDTH_DP, g.noteX[0] - c.noteX[0])
    }

    @Test
    fun `flat keys reserve nothing yet`() {
        assertNear(0f, staffLayout(noteText("C4w", KeySignature(-2)), WIDE).keyWidth)
        assertEquals(emptyList(), keySignaturePositions(-2, Staff.TREBLE))
    }

    @Test
    fun `key signature sharps sit in the standard positions`() {
        assertEquals(listOf(8, 5, 9, 6, 3, 7, 4), keySignaturePositions(7, Staff.TREBLE))
        assertEquals(listOf(-6, -9), keySignaturePositions(2, Staff.BASS))
        assertEquals(emptyList(), keySignaturePositions(0, Staff.TREBLE))
    }

    @Test
    fun `an up stem grows from the lowest head to 3 and a half spaces above the highest`() {
        val layout = staffLayout(noteText("[C4 E4]q rq rh"), WIDE)

        val stem = layout.chords.single().stem!!
        assertTrue(stem.up)
        assertEquals(-2, stem.fromPosition)
        assertEquals(0 + 7, stem.tipPosition)
        assertNear(layout.noteX[0] + 8.75f, stem.x)
        assertTrue(
            layout.chords
                .single()
                .dotPositions
                .isEmpty(),
        )
    }

    @Test
    fun `a down stem hangs on the left from the highest head`() {
        val layout = staffLayout(noteText("B4q rq rh"), WIDE)

        val stem = layout.chords.single().stem!!
        assertTrue(!stem.up)
        assertEquals(4, stem.fromPosition)
        assertEquals(4 - 7, stem.tipPosition)
        assertNear(layout.noteX[0] - 8.75f, stem.x)
    }

    @Test
    fun `a whole note has no stem`() {
        assertEquals(null, staffLayout(noteText("C4w"), WIDE).chords.single().stem)
    }

    @Test
    fun `a lone eighth is flagged at its stem tip`() {
        val layout = staffLayout(noteText("C4e re D4q E4h"), WIDE)

        assertTrue(layout.chords[0].flag)
        assertEquals(-2 + 7, layout.chords[0].stem!!.tipPosition)
        assertTrue(layout.beams.isEmpty())
    }

    @Test
    fun `a beam joins its stems at the farthest tip`() {
        val layout = staffLayout(noteText("C4e E4e rq rh"), WIDE)

        val beam = layout.beams.single()
        assertEquals(0 + 7, beam.position)
        assertTrue(beam.up)
        assertNear(layout.chords[0].stem!!.x, beam.x1)
        assertNear(layout.chords[1].stem!!.x, beam.x2)
        assertTrue(layout.chords.all { it.stem!!.tipPosition == 7 && !it.flag })
    }

    @Test
    fun `a down beam ends at the lowest tip`() {
        val layout = staffLayout(noteText("G4e B5e rq rh"), WIDE)

        val beam = layout.beams.single()
        assertTrue(!beam.up)
        assertEquals(2 - 7, beam.position)
    }

    @Test
    fun `a dot sits right of the head, in the space above when the head is on a line`() {
        val layout = staffLayout(noteText("E4q. G4e rh"), WIDE)

        val (online, onspace) = layout.chords
        assertEquals(listOf(1), online.dotPositions) // E4 is on the bottom line, position 0
        assertNear(layout.noteX[0] + 17f, online.dotX)
        assertTrue(onspace.dotPositions.isEmpty())
        assertEquals(listOf(1), staffLayout(noteText("F4h. rq"), WIDE).chords.single().dotPositions) // F4: a space
    }

    @Test
    fun `a chord's dots in one space are drawn once`() {
        val layout = staffLayout(noteText("[C4 D4]h. rq"), WIDE)

        assertEquals(listOf(-1), layout.chords.single().dotPositions)
    }

    @Test
    fun `a rest gets space like a note`() {
        val layout = staffLayout(noteText("C4q rq E4h"), WIDE)

        assertNear(layout.restX.single() - layout.noteX[0], layout.noteX[1] - layout.restX.single())
    }

    @Test
    fun `a trailing rest moves the final bar out`() {
        val layout = staffLayout(noteText("C4q rh."), WIDE)

        assertTrue(layout.finalBarX > layout.restX.last())
        assertEquals(2, layout.restX.size)
    }

    @Test
    fun `a whole rest fills its bar in any metre`() {
        val three = TimeSignature(3, 4)
        val layout =
            staffLayout(
                Passage("t", "T", NoteText.parse("rq rq rq | C4h.", three), three, 100, TEST_RIGHTS),
                WIDE,
            )

        assertEquals(1, layout.restX.size)
        assertEquals(1, layout.barLineX.size)
        assertTrue(layout.barLineX.single() < layout.noteX[0])
    }

    @Test
    fun `every head of a chord follows the chord's value`() {
        val layout = staffLayout(noteText("[C4 E4]h rh"), WIDE)
        val mixed =
            staffLayout(
                passage(Step(0, setOf(ScoreNote(c4, 4 * Q), ScoreNote(e4, Q)))),
                WIDE,
            )

        assertEquals(listOf(setOf(c4, e4)), layout.hollow)
        assertEquals(listOf(emptySet()), mixed.hollow)
    }

    @Test
    fun `a held bass under a quarter melody note keeps its own hollow head`() {
        val c3 = MidiNote(48)
        val layout = staffLayout(passage(Step(0, setOf(ScoreNote(c3, 4 * Q), ScoreNote(e4, Q)))), WIDE)

        assertEquals(listOf(setOf(c3)), layout.hollow)
    }

    @Test
    fun `the staff box is taller for a grand staff`() {
        assertEquals(SINGLE_STAFF_HEIGHT_DP, staffHeightDp(Engraving.engrave(noteText("C4w"))))
        assertEquals(SINGLE_STAFF_HEIGHT_DP, staffHeightDp(Engraving.engrave(noteText("C3w"))))
        assertEquals(GRAND_STAFF_HEIGHT_DP, staffHeightDp(Engraving.engrave(noteText("[C3 C4]w"))))
        // Two staves of 64 dp, a 32 dp gap and 40 dp above and below.
        assertTrue(GRAND_STAFF_HEIGHT_DP >= 160f + 2 * 40f)
    }

    @Test
    fun `pedal lines sit at the x of their ticks with a notch at each change`() {
        val bar = 4 * Q
        val spans = listOf(PedalSpan(0, bar), PedalSpan(bar, 2 * bar))
        val marked = passage(*quarters(8)).copy(pedal = spans)
        val layout = staffLayout(marked, WIDE)
        val line = layout.pedal.single()
        assertNear(layout.noteX[0], line.x1)
        assertNear(layout.noteX[4], line.notchXs.single())
        assertNear(layout.finalBarX, line.x2)
        assertTrue(staffLayout(passage(*quarters(4)), WIDE).pedal.isEmpty())
    }
}
