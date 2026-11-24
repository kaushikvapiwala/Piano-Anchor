package pianoanchor.engine.sight

import pianoanchor.core.model.Passage
import pianoanchor.core.model.RightsStatus
import pianoanchor.core.model.TICKS_PER_QUARTER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SEEDS = 200
private const val DISTINCT_SEEDS = 20
private const val MIN_DISTINCT = 15
private const val LEVELS = 8
private const val LAST_LEVEL = 8
private const val FIRST_HAND_SPLIT = 60
private val RIGHT_HAND = setOf(60, 62, 64, 65, 67)
private val LEFT_HAND = setOf(48, 50, 52, 53, 55)
private val WHITE_PITCH_CLASSES = listOf(0, 2, 4, 5, 7)
private val C_CHORD = setOf(0, 4, 7)
private val F_CHORD = setOf(5, 9, 0)
private val G_CHORD = setOf(7, 11, 2)

class SightReadingGeneratorTest {
    private fun forEveryPassage(check: (Int, Passage) -> Unit) {
        for (level in 1..LEVELS) {
            for (seed in 0L until SEEDS) check(level, SightReadingGenerator.passage(level, seed))
        }
    }

    /** The notes the learner reads as the tune: the right hand from level 7, every note before. */
    private fun tune(
        level: Int,
        passage: Passage,
    ) = passage.steps.mapNotNull { step ->
        step.notes
            .map { it.pitch.number }
            .filter { level < 7 || it >= FIRST_HAND_SPLIT }
            .singleOrNull()
    }

    private fun bars(passage: Passage) = passage.steps.groupBy { it.onsetTicks / passage.timeSignature.barTicks }

    @Test
    fun sameLevelAndSeedGiveEqualPassages() {
        for (level in 1..LEVELS) {
            assertEquals(SightReadingGenerator.passage(level, 7), SightReadingGenerator.passage(level, 7))
        }
    }

    @Test
    fun differentSeedsDiffer() {
        for (level in 1..LEVELS) {
            val distinct = (0L until DISTINCT_SEEDS).map { SightReadingGenerator.passage(level, it).steps }.toSet()
            assertTrue(distinct.size >= MIN_DISTINCT, "level $level had only ${distinct.size} distinct exercises")
        }
    }

    @Test
    fun passageMetadataFollowsTheSpec() {
        forEveryPassage { level, passage ->
            assertEquals("sight:$level:${passage.id.substringAfterLast(':')}", passage.id)
            assertEquals(RightsStatus.ORIGINAL, passage.rights.status)
            assertTrue(passage.skills.isEmpty())
            assertEquals(false, passage.piece)
            val expectedBeats = if (level == 6) 3 else 4
            assertEquals(expectedBeats, passage.timeSignature.beats)
        }
    }

    @Test
    fun staysInRange() {
        forEveryPassage { level, passage ->
            val allowed =
                when (level) {
                    in 1..3 -> RIGHT_HAND
                    4 -> LEFT_HAND
                    5, 6 -> RIGHT_HAND + LEFT_HAND
                    else -> RIGHT_HAND + LEFT_HAND + setOf(41, 43, 45, 47, 57, 59)
                }
            passage.steps.forEach { step ->
                step.notes.forEach { assertTrue(it.pitch.number in allowed, "level $level: ${it.pitch.number}") }
            }
        }
    }

    @Test
    fun usesOnlyAllowedIntervals() {
        forEveryPassage { level, passage ->
            val maxJump = if (level == 1) 1 else 2
            tune(level, passage)
                .map { WHITE_PITCH_CLASSES.indexOf(it % 12) }
                .zipWithNext()
                .forEach { (a, b) -> assertTrue(kotlin.math.abs(a - b) <= maxJump, "level $level: $a to $b") }
        }
    }

    @Test
    fun barsAreFull() {
        forEveryPassage { level, passage ->
            val barTicks = passage.timeSignature.barTicks
            bars(passage).forEach { (bar, steps) ->
                assertEquals(bar * barTicks, steps.first().onsetTicks, "level $level bar $bar starts on beat 1")
                val rightHand =
                    steps.mapNotNull { s ->
                        s.notes.filter { level < 7 || it.pitch.number >= 60 }.singleOrNull()
                    }
                val played = rightHand.sumOf { it.lengthTicks }
                assertTrue(
                    played == barTicks || played == barTicks - TICKS_PER_QUARTER,
                    "level $level bar $bar plays $played of $barTicks ticks",
                )
                steps.forEach { s ->
                    s.notes.forEach { assertTrue(s.onsetTicks + it.lengthTicks <= (bar + 1) * barTicks) }
                }
            }
        }
    }

    @Test
    fun hasFourBars() {
        forEveryPassage { _, passage -> assertEquals(listOf(0, 1, 2, 3), bars(passage).keys.sorted()) }
    }

    @Test
    fun startsAndEndsOnC() {
        forEveryPassage { level, passage ->
            val notes = tune(level, passage)
            assertEquals(0, notes.first() % 12)
            assertEquals(0, notes.last() % 12)
            if (level in 4..6) assertEquals(48, notes.last())
            val lastBar = bars(passage).getValue(3)
            val lengths =
                lastBar
                    .flatMap { s ->
                        s.notes.filter { level < 7 || it.pitch.number >= 60 }
                    }.map { it.lengthTicks }
            assertEquals(lengths.max(), lengths.last())
        }
    }

    @Test
    fun level8ChordsAreCThenFOrGThenC() {
        for (seed in 0L until SEEDS) {
            val passage = SightReadingGenerator.passage(LAST_LEVEL, seed)
            val chords =
                bars(passage).toSortedMap().map { (_, steps) ->
                    val left =
                        steps
                            .first()
                            .notes
                            .map { it.pitch.number }
                            .filter { it < FIRST_HAND_SPLIT }
                    assertEquals(3, left.size)
                    assertEquals(
                        passage.timeSignature.barTicks,
                        steps
                            .first()
                            .notes
                            .filter {
                                it.pitch.number < 60
                            }.map { it.lengthTicks }
                            .distinct()
                            .single(),
                    )
                    val right =
                        steps
                            .first()
                            .notes
                            .map { it.pitch.number }
                            .single { it >= FIRST_HAND_SPLIT }
                    val classes = left.map { it % 12 }.toSet()
                    assertTrue(right % 12 in classes, "RH on beat 1 belongs to the chord")
                    classes
                }
            assertEquals(C_CHORD, chords[0])
            assertTrue(chords[1] == F_CHORD || chords[1] == G_CHORD)
            assertTrue(chords[2] == F_CHORD || chords[2] == G_CHORD)
            assertEquals(C_CHORD, chords[3])
        }
    }

    @Test
    fun level7HoldsOneLeftHandNotePerBar() {
        for (seed in 0L until SEEDS) {
            val passage = SightReadingGenerator.passage(7, seed)
            bars(passage).forEach { (_, steps) ->
                val left = steps.first().notes.filter { it.pitch.number < FIRST_HAND_SPLIT }
                assertEquals(1, left.size)
                assertTrue(left.single().pitch.number in setOf(48, 55))
                assertEquals(passage.timeSignature.barTicks, left.single().lengthTicks)
            }
            assertEquals(
                48,
                bars(passage)
                    .getValue(3)
                    .first()
                    .notes
                    .minOf { it.pitch.number },
            )
        }
    }

    @Test
    fun level5TakesTurnsStartingWithTheRightHand() {
        for (seed in 0L until SEEDS) {
            bars(SightReadingGenerator.passage(5, seed)).forEach { (bar, steps) ->
                val hand = if (bar % 2 == 0) RIGHT_HAND else LEFT_HAND
                steps.forEach { s -> s.notes.forEach { assertTrue(it.pitch.number in hand) } }
            }
        }
    }

    @Test
    fun idRoundTrips() {
        assertEquals(3 to 42L, SightReadingGenerator.parse(SightReadingGenerator.idOf(3, 42)))
        assertEquals(2 to -5L, SightReadingGenerator.parse(SightReadingGenerator.idOf(2, -5)))
        assertEquals("sight:3:42", SightReadingGenerator.passage(3, 42).id)
    }

    @Test
    fun parseRejectsOtherIds() {
        listOf("ode-to-joy", "sight:x:1", "sight:9:1", "sight:0:1", "sight:1:y", "sight:1", "song:1:1", "sight:1:2:3")
            .forEach { assertNull(SightReadingGenerator.parse(it), it) }
    }

    @Test
    fun fromIdRebuildsThePassageOfAnIdOrIsNull() {
        assertEquals(SightReadingGenerator.passage(4, 7), SightReadingGenerator.fromId("sight:4:7"))
        assertNull(SightReadingGenerator.fromId("ode-to-joy"))
    }

    @Test
    fun levelOutsideRangeThrows() {
        assertFailsWith<IllegalArgumentException> { SightReadingGenerator.passage(0, 1) }
        assertFailsWith<IllegalArgumentException> { SightReadingGenerator.passage(9, 1) }
    }
}
