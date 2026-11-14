package pianoanchor.testing

import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Finding
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.ScoreNoteRef
import pianoanchor.core.model.Step
import pianoanchor.core.model.TimeSignature
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ErrorInjectorTest {
    private val c4 = MidiNote(60)
    private val d4 = MidiNote(62)
    private val e4 = MidiNote(64)
    private val g4 = MidiNote(67)
    private val b4 = MidiNote(71)

    // Quarter notes one beat (1 s at 60 BPM) apart; step 3 is a chord.
    private val passage = passageOf(setOf(c4), setOf(e4), setOf(g4), setOf(c4, e4, g4), setOf(d4), setOf(b4))
    private val seeds = 0L until 30L

    // Timing tolerance at 60 BPM: max(60 ms, 12.5 % of 1 s) = 125 ms.
    private val tolerance = 125_000_000L

    private class Injected(
        val clean: LabelledTake,
        val take: LabelledTake,
    ) {
        val label: Finding = take.labels.single()
        val original: PlayedNote get() = clean.nearest(label.at.pitch, clean.writtenOnsetNanos(label.at.stepIndex))
        val added: List<PlayedNote> get() = take.notes - clean.notes.toSet()
        val removed: List<PlayedNote> get() = clean.notes - take.notes.toSet()
    }

    private fun forEachInjection(
        kind: ErrorKind,
        check: Injected.() -> Unit,
    ) = seeds.forEach { seed ->
        val clean = TakeGenerator(seed).clean(passage, 60)
        val injected = Injected(clean, ErrorInjector(seed).inject(clean, kind))
        assertEquals(kind, injected.label.kind, "seed $seed")
        assertEquals(
            injected.take.notes.sortedWith(compareBy({ it.downNanos }, { it.pitch.number })),
            injected.take.notes,
        )
        injected.check()
    }

    private fun isChord(ref: ScoreNoteRef) = passage.steps[ref.stepIndex].notes.size > 1

    @Test
    fun wrongNoteMovesASingleNoteBy3To11SemitonesAndLabelsIt() =
        forEachInjection(ErrorKind.WRONG_NOTE) {
            assertTrue(!isChord(label.at))
            val played = assertNotNull(label.played)
            assertEquals(listOf(original), removed)
            assertEquals(listOf(played), added)
            assertTrue(abs(played.pitch.number - label.at.pitch.number) in 3..11)
            assertEquals(original.copy(pitch = played.pitch), played)
        }

    @Test
    fun missedNoteDropsASingleNoteAndLabelsIt() =
        forEachInjection(ErrorKind.MISSED_NOTE) {
            assertTrue(!isChord(label.at))
            assertNull(label.played)
            assertEquals(listOf(original), removed)
            assertTrue(added.isEmpty())
        }

    @Test
    fun extraNoteAddsADistantNoteAwayFromEveryOnsetAndLabelsTheNearestStep() =
        forEachInjection(ErrorKind.EXTRA_NOTE) {
            val extra = added.single()
            assertEquals(extra, label.played)
            assertTrue(removed.isEmpty())
            assertTrue(passage.steps.flatMap { it.pitches }.all { abs(it.number - extra.pitch.number) >= 3 })
            assertTrue(clean.notes.all { abs(it.downNanos - extra.downNanos) >= 120_000_000L })
            val nearest = passage.steps.indices.minBy { abs(clean.writtenOnsetNanos(it) - extra.downNanos) }
            assertEquals(ScoreNoteRef(nearest, passage.steps[nearest].pitches.minBy { it.number }), label.at)
        }

    @Test
    fun extraNoteSkipsGapsWithAnOnsetTooClose() {
        // Two steps 1 s apart; a stray note at 40 % of the gap blocks the only place an extra note could go.
        val twoSteps = passageOf(setOf(c4), setOf(e4))
        val clean = TakeGenerator(1).clean(twoSteps, 60)
        val stray =
            PlayedNote(
                b4,
                64,
                TAKE_START_NANOS + 400_000_000L,
                TAKE_START_NANOS + 450_000_000L,
                TAKE_START_NANOS + 450_000_000L,
            )
        assertFailsWith<IllegalArgumentException> {
            ErrorInjector(1).inject(clean.copy(notes = clean.notes + stray), ErrorKind.EXTRA_NOTE)
        }
    }

    @Test
    fun extraNoteIsRefusedWhenStepsAreTooClose() {
        // At 300 BPM steps are 200 ms apart: no gap leaves room 120 ms from every onset.
        val clean = TakeGenerator(1).clean(passage, 300)
        assertFailsWith<IllegalArgumentException> { ErrorInjector(1).inject(clean, ErrorKind.EXTRA_NOTE) }
    }

    @Test
    fun incompleteChordDropsOneChordNoteAndLabelsIt() =
        forEachInjection(ErrorKind.INCOMPLETE_CHORD) {
            assertTrue(isChord(label.at))
            assertNull(label.played)
            assertEquals(listOf(original), removed)
            assertTrue(added.isEmpty())
        }

    @Test
    fun incompleteChordIsRefusedOnAPassageWithoutChords() {
        val melody = passageOf(setOf(c4), setOf(e4), setOf(g4))
        val clean = TakeGenerator(1).clean(melody, 60)
        assertFailsWith<IllegalArgumentException> { ErrorInjector(1).inject(clean, ErrorKind.INCOMPLETE_CHORD) }
    }

    @Test
    fun mistouchAddsASofterNeighbourWithin40MsOfACorrectNoteAndLabelsIt() =
        forEachInjection(ErrorKind.MISTOUCH) {
            val touch = added.single()
            assertEquals(touch, label.played)
            assertTrue(removed.isEmpty())
            assertTrue(abs(touch.pitch.number - label.at.pitch.number) in 1..2)
            assertTrue(touch.pitch !in passage.steps[label.at.stepIndex].pitches)
            assertTrue(touch.downNanos - original.downNanos in 0..40_000_000L)
            assertTrue(touch.velocity < original.velocity)
        }

    @Test
    fun octaveShiftsOneNoteBy12AndLabelsIt() =
        forEachInjection(ErrorKind.OCTAVE) {
            val played = assertNotNull(label.played)
            assertEquals(listOf(original), removed)
            assertEquals(listOf(played), added)
            assertEquals(12, abs(played.pitch.number - label.at.pitch.number))
            assertEquals(original.copy(pitch = played.pitch), played)
        }

    private fun Injected.assertShiftedWithinOrder(sign: Int) {
        val played = assertNotNull(label.played)
        assertEquals(listOf(original), removed)
        assertEquals(listOf(played), added)
        val shift = played.downNanos - original.downNanos
        assertTrue(sign * shift in 2 * tolerance..3 * tolerance, "shift $shift")
        assertEquals(original.upNanos + shift, played.upNanos)
        assertEquals(original.soundingEndNanos + shift, played.soundingEndNanos)
        val step = label.at.stepIndex

        fun downs(i: Int) = passage.steps[i].pitches.map { clean.nearest(it, clean.writtenOnsetNanos(i)).downNanos }
        if (step > 0) assertTrue(downs(step - 1).all { it < played.downNanos })
        if (step < passage.steps.lastIndex) assertTrue(downs(step + 1).all { it > played.downNanos })
    }

    @Test
    fun earlyShiftsAnOnsetBy2To3TolerancesKeepingStepOrder() =
        forEachInjection(ErrorKind.EARLY) {
            assertShiftedWithinOrder(-1)
        }

    @Test
    fun lateShiftsAnOnsetBy2To3TolerancesKeepingStepOrder() =
        forEachInjection(ErrorKind.LATE) {
            assertShiftedWithinOrder(1)
        }

    @Test
    fun hesitationDelaysAStepAndEveryLaterStep() =
        forEachInjection(ErrorKind.HESITATION) {
            val k = label.at.stepIndex
            assertTrue(k >= 1)
            assertEquals(passage.steps[k].pitches.minBy { it.number }, label.at.pitch)
            val delay = assertNotNull(label.played).downNanos - original.downNanos
            assertTrue(delay >= 2_500_000_000L && delay >= 500_000_000L, "delay $delay")
            assertTrue(delay <= 4_000_000_000L, "delay $delay")
            passage.steps.forEachIndexed { i, step ->
                step.pitches.forEach { pitch ->
                    val before = clean.nearest(pitch, clean.writtenOnsetNanos(i))
                    val expected =
                        if (i >=
                            k
                        ) {
                            before.copy(
                                downNanos = before.downNanos + delay,
                                upNanos = before.upNanos + delay,
                                soundingEndNanos =
                                    before.soundingEndNanos + delay,
                            )
                        } else {
                            before
                        }
                    assertTrue(expected in take.notes, "step $i $pitch")
                }
            }
        }

    @Test
    fun hesitationIsRefusedOnAOneStepPassage() {
        val clean = TakeGenerator(1).clean(passageOf(setOf(c4)), 60)
        assertFailsWith<IllegalArgumentException> { ErrorInjector(1).inject(clean, ErrorKind.HESITATION) }
    }

    private fun Injected.assertLength(range: ClosedFloatingPointRange<Double>) {
        val played = assertNotNull(label.played)
        assertEquals(listOf(original), removed)
        assertEquals(original.downNanos, played.downNanos)
        assertEquals(played.upNanos, played.soundingEndNanos)
        // Every written note is a quarter: 1 s at 60 BPM.
        val fraction = (played.upNanos - played.downNanos) / 1e9
        assertTrue(fraction in range, "fraction $fraction")
    }

    @Test
    fun tooShortCutsANoteTo20To40PercentOfWritten() =
        forEachInjection(ErrorKind.TOO_SHORT) { assertLength(0.199..0.401) }

    @Test
    fun tooLongHoldsANoteFor180To250PercentOfWrittenWithoutPedal() =
        forEachInjection(ErrorKind.TOO_LONG) {
            assertLength(1.799..2.501)
        }

    @Test
    fun tooLongNeverOverlapsTheNextStrikeOfTheSamePitch() =
        forEachInjection(ErrorKind.TOO_LONG) {
            take.notes.groupBy { it.pitch }.values.forEach { same ->
                same.zipWithNext().forEach { (a, b) -> assertTrue(a.soundingEndNanos < b.downNanos, "$a then $b") }
            }
        }

    @Test
    fun pitchChangesNearTheEndsOfTheKeyboardStayInsideTheMidiRange() {
        // Notes 1 and 126: offsets that fall outside 0..127 must not be offered (MidiNote would throw).
        val edges = passageOf(setOf(MidiNote(1)), setOf(MidiNote(126)))
        seeds.forEach { seed ->
            val clean = TakeGenerator(seed).clean(edges, 60)
            listOf(ErrorKind.WRONG_NOTE, ErrorKind.OCTAVE, ErrorKind.MISTOUCH).forEach { kind ->
                val label = ErrorInjector(seed).inject(clean, kind).labels.single()
                val distance = assertNotNull(label.played).pitch.number - label.at.pitch.number
                // An octave can only go up from note 1 and down from note 126.
                if (kind == ErrorKind.OCTAVE) assertEquals(if (label.at.pitch.number == 1) 12 else -12, distance)
            }
        }
    }

    @Test
    fun timingShiftsOnlyPickNotesThatKeepStepOrder() {
        // Steps 125 ms apart at 60 BPM, shifts of 250–375 ms: only the first note can come early, only the last late.
        val pitches = listOf(c4, e4, g4)
        val close =
            Passage(
                id = "test:close",
                title = "Close",
                steps = pitches.mapIndexed { i, pitch -> Step(i * 120, setOf(ScoreNote(pitch, 120))) },
                timeSignature = TimeSignature(4, 4),
                tempoBpm = 60,
            )
        seeds.forEach { seed ->
            val clean = TakeGenerator(seed).clean(close, 60)
            assertEquals(
                0,
                ErrorInjector(seed)
                    .inject(clean, ErrorKind.EARLY)
                    .labels
                    .single()
                    .at.stepIndex,
            )
            assertEquals(
                2,
                ErrorInjector(seed)
                    .inject(clean, ErrorKind.LATE)
                    .labels
                    .single()
                    .at.stepIndex,
            )
        }
    }

    @Test
    fun sameSeedGivesTheSameInjection() {
        val clean = TakeGenerator(5).clean(passage, 60)
        ErrorKind.entries.forEach { kind ->
            assertEquals(ErrorInjector(9).inject(clean, kind), ErrorInjector(9).inject(clean, kind), "$kind")
        }
    }

    @Test
    fun refusesATakeThatAlreadyHasLabels() {
        val clean = TakeGenerator(1).clean(passage, 60)
        val once = ErrorInjector(1).inject(clean, ErrorKind.OCTAVE)
        assertFailsWith<IllegalArgumentException> { ErrorInjector(1).inject(once, ErrorKind.LATE) }
    }
}
