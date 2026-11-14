package pianoanchor.testing

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PlayedNote
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step
import pianoanchor.core.model.TimeSignature
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TakeGeneratorTest {
    private val c4 = MidiNote(60)
    private val e4 = MidiNote(64)
    private val g4 = MidiNote(67)
    private val b4 = MidiNote(71)
    private val passage = passageOf(setOf(c4), setOf(e4), setOf(c4, e4, g4), setOf(b4))
    private val seeds = 0L until 50L

    @Test
    fun sameSeedGivesTheSameTake() {
        assertEquals(TakeGenerator(7).clean(passage, 90), TakeGenerator(7).clean(passage, 90))
    }

    @Test
    fun aCleanTakeHasOneNotePerScoreNoteAndNoLabels() {
        val take = TakeGenerator(1).clean(passage, 60)
        assertEquals(
            listOf(c4, e4, c4, e4, g4, b4).sortedBy { it.number },
            take.notes.map { it.pitch }.sortedBy { it.number },
        )
        assertTrue(take.labels.isEmpty())
        assertEquals(60, take.tempoBpm)
        assertEquals(take.notes.sortedWith(compareBy({ it.downNanos }, { it.pitch.number })), take.notes)
    }

    @Test
    fun onsetsFollowTheWrittenRhythmAtTheTempoWithin4Sigma() {
        val limit = 4 * ONSET_JITTER_SIGMA_NANOS + CHORD_SPREAD_NANOS
        seeds.forEach { seed ->
            val take = TakeGenerator(seed).clean(passage, 120)
            passage.steps.forEachIndexed { i, step ->
                // At 120 BPM a beat is 500 ms; steps are one beat apart from TAKE_START_NANOS.
                assertEquals(TAKE_START_NANOS + i * 500_000_000L, take.writtenOnsetNanos(i))
                step.pitches.forEach { pitch ->
                    val note = take.nearest(pitch, take.writtenOnsetNanos(i))
                    assertTrue(abs(note.downNanos - take.writtenOnsetNanos(i)) <= limit, "seed $seed step $i: $note")
                }
            }
        }
    }

    @Test
    fun velocitiesAndLengthsStayInRange() {
        seeds.forEach { seed ->
            val take = TakeGenerator(seed).clean(passage, 60)
            take.notes.forEach { note ->
                assertTrue(note.velocity in CLEAN_VELOCITY_RANGE, "seed $seed: $note")
                // Quarter notes at 60 BPM last 1 s.
                val fraction = (note.upNanos - note.downNanos) / 1e9
                assertTrue(fraction in 0.799..0.951, "seed $seed: $note lasts $fraction of written")
                assertEquals(note.upNanos, note.soundingEndNanos)
            }
        }
    }

    @Test
    fun aRepeatedPitchComesUpBeforeItIsStruckAgain() {
        // At 300 BPM quarters are 200 ms apart and last 160–190 ms, so jitter would often make them overlap.
        val repeated = passageOf(setOf(c4), setOf(c4), setOf(c4), setOf(c4), setOf(c4), setOf(c4))
        seeds.forEach { seed ->
            TakeGenerator(seed).clean(repeated, 300).notes.zipWithNext().forEach { (a, b) ->
                assertTrue(
                    a.upNanos <= b.downNanos - REPEAT_GAP_NANOS || a.upNanos == a.downNanos,
                    "seed $seed: $a then $b",
                )
            }
        }
    }

    @Test
    fun stepsCloserThanTheJitterKeepTheirOrder() {
        // Steps 25 ms apart (24 ticks at 60 BPM), well inside the jitter's spread, the middle one a chord.
        val pitches = listOf(setOf(c4), setOf(e4, g4), setOf(b4), setOf(MidiNote(72)))
        val fast =
            Passage(
                id = "test:fast",
                title = "Fast",
                steps =
                    pitches.mapIndexed {
                        i,
                        step,
                        ->
                        Step(i * 24, step.mapTo(LinkedHashSet()) { ScoreNote(it, 24) })
                    },
                timeSignature = TimeSignature(4, 4),
                tempoBpm = 60,
            )
        seeds.forEach { seed ->
            val take = TakeGenerator(seed).clean(fast, 60)
            val stepOf = pitches.withIndex().flatMap { (i, step) -> step.map { it to i } }.toMap()
            assertEquals(
                take.notes
                    .map {
                        stepOf.getValue(it.pitch)
                    }.sorted(),
                take.notes.map { stepOf.getValue(it.pitch) },
                "seed $seed",
            )
        }
    }

    @Test
    fun gaussianDrawsHaveUnitSpread() {
        val random = Random(3)
        val draws = List(20_000) { random.nextGaussian() }
        val mean = draws.average()
        val variance = draws.sumOf { (it - mean) * (it - mean) } / draws.size
        assertTrue(abs(mean) < 0.05, "mean $mean")
        assertTrue(abs(variance - 1) < 0.05, "variance $variance")
    }

    @Test
    fun rejectsATempoOutOfRange() {
        assertFailsWith<IllegalArgumentException> { TakeGenerator(1).clean(passage, 19) }
        assertFailsWith<IllegalArgumentException> { TakeGenerator(1).clean(passage, 301) }
    }
}

/** The played note of [pitch] whose onset is nearest [nanos]. */
internal fun LabelledTake.nearest(
    pitch: MidiNote,
    nanos: Long,
): PlayedNote = notes.filter { it.pitch == pitch }.minBy { abs(it.downNanos - nanos) }
