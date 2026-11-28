package pianoanchor.engine.sight

import pianoanchor.core.model.KeySignature
import pianoanchor.core.model.Passage
import pianoanchor.core.model.PedalSpan
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
private const val LEVELS = 12
private const val LAST_LEVEL = 8
private const val FIRST_HAND_SPLIT = 60
private val RIGHT_HAND = setOf(60, 62, 64, 65, 67)
private val RIGHT_HAND_IN_G = listOf(67, 69, 71, 72, 74)
private val G_AND_D7_NOTES = setOf(42, 43, 47, 48, 50)
private const val FIRST_G_LEVEL = 11
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
                    FIRST_G_LEVEL -> RIGHT_HAND_IN_G.toSet()
                    FIRST_G_LEVEL + 1 -> RIGHT_HAND_IN_G.toSet() + G_AND_D7_NOTES
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
                .map {
                    if (level >=
                        FIRST_G_LEVEL
                    ) {
                        RIGHT_HAND_IN_G.indexOf(it)
                    } else {
                        WHITE_PITCH_CLASSES.indexOf(it % 12)
                    }
                }.zipWithNext()
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
    fun startsAndEndsOnTheTonic() {
        forEveryPassage { level, passage ->
            val notes = tune(level, passage)
            val tonic = if (level >= FIRST_G_LEVEL) 7 else 0
            assertEquals(tonic, notes.first() % 12)
            assertEquals(tonic, notes.last() % 12)
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

    /** The rhythm of each bar of the right-hand tune, in ticks. */
    private fun cells(passage: Passage) =
        bars(passage).toSortedMap().values.map { steps -> steps.map { step -> step.notes.maxOf { it.lengthTicks } } }

    @Test
    fun level9AndLevel10UseTheirNewCells() {
        val level9 =
            listOf(
                listOf(480, 480, 960, 960, 960),
                listOf(960, 960, 480, 480, 960),
                listOf(960, 480, 480, 1920),
                listOf(480, 480, 480, 480, 1920),
            )
        val level10 = listOf(listOf(1440, 480, 960, 960), listOf(1440, 480, 1920), listOf(960, 1440, 480, 960))
        for ((level, added, earlier) in listOf(Triple(9, level9, emptyList()), Triple(10, level10, level9))) {
            val seen = (0L until SEEDS).flatMap { cells(SightReadingGenerator.passage(level, it)) }.toSet()
            assertTrue(added.all { it in seen }, "level $level never drew one of its new cells")
            assertTrue(earlier.all { it in seen }, "level $level lost a cell of the level before")
        }
        val level8Cells = (0L until SEEDS).flatMap { cells(SightReadingGenerator.passage(8, it)) }.toSet()
        assertTrue((level9 + level10).none { it in level8Cells })
        val nineCells = (0L until SEEDS).flatMap { cells(SightReadingGenerator.passage(9, it)) }.toSet()
        assertTrue(level10.none { it in nineCells })
    }

    @Test
    fun level9AndLevel10CloseOnALongC() {
        for (level in 9..10) {
            for (seed in 0L until SEEDS) {
                val passage = SightReadingGenerator.passage(level, seed)
                val last = cells(passage).last()
                assertEquals(last.max(), last.last(), "level $level seed $seed")
                assertEquals(0, tune(level, passage).last() % 12)
            }
        }
    }

    @Test
    fun levels1To13KeepTheirOutputForAFixedSeed() {
        val golden =
            mapOf(
                1 to -1403727910,
                2 to -1360771874,
                3 to 1231011641,
                4 to -986532498,
                5 to -146363772,
                6 to -995799761,
                7 to -481410807,
                8 to -285839106,
                9 to -1169345127,
                10 to -976897058,
                11 to 966084911,
                12 to -91969490,
                13 to -285839106,
            )
        golden.forEach { (level, hash) ->
            val text =
                SightReadingGenerator.passage(level, 42).steps.joinToString(",") { step ->
                    "${step.onsetTicks}:" +
                        step.notes
                            .map { "${it.pitch.number}/${it.lengthTicks}" }
                            .sorted()
                            .joinToString("+")
                }
            assertEquals(hash, text.hashCode(), "level $level changed")
        }
        for (level in 1..10) {
            for (seed in 0L until SEEDS) {
                assertEquals(KeySignature.C, SightReadingGenerator.passage(level, seed).keySignature)
            }
        }
    }

    @Test
    fun level11StaysInGPositionStartsAndEndsOnGInTheKeyOfG() {
        val g = setOf(67, 69, 71, 72, 74)
        for (seed in 0L until SEEDS) {
            val passage = SightReadingGenerator.passage(11, seed)
            assertEquals(KeySignature.G, passage.keySignature)
            assertEquals(4, passage.timeSignature.beats)
            val tune =
                passage.steps.map { step ->
                    step.notes
                        .single()
                        .pitch.number
                }
            assertTrue(tune.all { it in g }, "seed $seed")
            assertEquals(67, tune.first(), "seed $seed")
            assertEquals(67, tune.last(), "seed $seed")
            tune.zipWithNext().forEach { (a, b) -> assertTrue(kotlin.math.abs(g.indexOf(a) - g.indexOf(b)) <= 2) }
        }
    }

    @Test
    fun level12FollowsGThenGOrD7ThenD7ThenGWithChordTonesOnBeatOne() {
        val gChord = setOf(43, 47, 50)
        val d7 = setOf(42, 48, 50)
        val seenSecondBars = mutableSetOf<Set<Int>>()
        for (seed in 0L until SEEDS) {
            val passage = SightReadingGenerator.passage(12, seed)
            assertEquals(KeySignature.G, passage.keySignature)
            val barSteps = bars(passage).toSortedMap().values.toList()
            assertEquals(4, barSteps.size)
            val chords =
                barSteps.map { steps ->
                    steps
                        .first()
                        .notes
                        .filter { it.pitch.number < 60 }
                        .map { it.pitch.number }
                        .toSet()
                }
            assertEquals(gChord, chords[0], "seed $seed")
            assertTrue(chords[1] == gChord || chords[1] == d7, "seed $seed")
            assertEquals(d7, chords[2], "seed $seed")
            assertEquals(gChord, chords[3], "seed $seed")
            seenSecondBars += chords[1]
            barSteps.forEachIndexed { bar, steps ->
                val tones = if (chords[bar] == d7) setOf(69, 72, 74) else setOf(67, 71, 74)
                val first = steps.first().notes.single { it.pitch.number >= 60 }
                assertTrue(first.pitch.number in tones, "seed $seed bar $bar")
                assertTrue(
                    steps.flatMap { it.notes }.filter { it.pitch.number >= 60 }.all { it.pitch.number in 67..74 },
                )
                steps.drop(1).forEach { assertEquals(1, it.notes.size, "only the first step of a bar has the chord") }
            }
            val tune =
                barSteps.flatten().map { s ->
                    s.notes
                        .single { it.pitch.number >= 60 }
                        .pitch.number
                }
            assertEquals(67, tune.first())
            assertEquals(67, tune.last())
        }
        assertEquals(setOf(gChord, d7), seenSecondBars)
    }

    @Test
    fun levels11And12UseTheLevel10Cells() {
        fun rightHandCells(passage: Passage) =
            bars(passage).toSortedMap().values.map { steps ->
                steps.map { step -> step.notes.filter { it.pitch.number >= FIRST_HAND_SPLIT }.maxOf { it.lengthTicks } }
            }
        val level10Cells = (0L until SEEDS).flatMap { rightHandCells(SightReadingGenerator.passage(10, it)) }.toSet()
        for (level in 11..12) {
            val seen = (0L until SEEDS).flatMap { rightHandCells(SightReadingGenerator.passage(level, it)) }.toSet()
            assertEquals(level10Cells, seen, "level $level")
        }
    }

    @Test
    fun level13DrawsLevel8MaterialInCOrLevel12MaterialInGWithOnePedalSpanPerBar() {
        val seenKeys = mutableSetOf<KeySignature>()
        for (seed in 0L until SEEDS) {
            val passage = SightReadingGenerator.passage(13, seed)
            assertEquals("sight:13:$seed", passage.id)
            assertEquals(4, bars(passage).size)
            val bar = passage.timeSignature.barTicks
            assertEquals((0 until 4).map { PedalSpan(it * bar, (it + 1) * bar) }, passage.pedal, "seed $seed")
            val base = if (passage.keySignature == KeySignature.C) 8 else 12
            seenKeys += passage.keySignature
            val sameMaterial = SightReadingGenerator.passage(base, seed)
            assertEquals(sameMaterial.steps, passage.steps, "seed $seed")
            assertEquals(RightsStatus.ORIGINAL, passage.rights.status)
            assertTrue(passage.skills.isEmpty())
            assertEquals(false, passage.piece)
        }
        assertEquals(setOf(KeySignature.C, KeySignature.G), seenKeys)
    }

    @Test
    fun level13VariesBarTwoChordsInBothKeys() {
        val barTwoChords = mutableMapOf<KeySignature, MutableSet<Set<Int>>>()
        for (seed in 0L until SEEDS) {
            val passage = SightReadingGenerator.passage(13, seed)
            val bar = passage.timeSignature.barTicks
            val chord =
                passage.steps
                    .first { it.onsetTicks == bar }
                    .notes
                    .map { it.pitch.number }
                    .filter { it < 60 }
                    .toSet()
            barTwoChords.getOrPut(passage.keySignature) { mutableSetOf() } += chord
        }
        assertTrue(barTwoChords.getValue(KeySignature.C).size > 1, "C: ${barTwoChords[KeySignature.C]}")
        assertTrue(barTwoChords.getValue(KeySignature.G).size > 1, "G: ${barTwoChords[KeySignature.G]}")
    }

    @Test
    fun levels1To12CarryNoPedalMarks() {
        forEveryPassage { level, passage -> assertTrue(passage.pedal.isEmpty(), "level $level") }
    }

    @Test
    fun level13IsRebuiltFromItsId() {
        assertEquals(SightReadingGenerator.passage(13, 9), SightReadingGenerator.fromId("sight:13:9"))
        assertEquals(13 to 9L, SightReadingGenerator.parse("sight:13:9"))
    }

    @Test
    fun level14StaysInTheOctaveStartsAndEndsOnC() {
        for (seed in 0L until SEEDS) {
            val passage = SightReadingGenerator.passage(14, seed)
            val notes =
                passage.steps.map { step ->
                    step.notes
                        .single()
                        .pitch.number
                }
            assertTrue(notes.all { it in 60..72 }, "seed $seed")
            assertTrue(notes.first() in listOf(60, 72), "seed $seed starts on ${notes.first()}")
            assertTrue(notes.last() in listOf(60, 72), "seed $seed ends on ${notes.last()}")
            assertTrue(notes.all { WHITE_PITCH_CLASSES.contains(it % 12) || it % 12 in listOf(9, 11) })
            assertEquals(KeySignature.C, passage.keySignature)
            assertTrue(passage.pedal.isEmpty())
            assertEquals("sight:14:$seed", passage.id)
        }
    }

    @Test
    fun level14MovesByStepsAndThirds() {
        val keys = listOf(60, 62, 64, 65, 67, 69, 71, 72)
        for (seed in 0L until SEEDS) {
            SightReadingGenerator
                .passage(14, seed)
                .steps
                .map {
                    keys.indexOf(
                        it.notes
                            .single()
                            .pitch.number,
                    )
                }.zipWithNext()
                .forEach { (a, b) -> assertTrue(kotlin.math.abs(a - b) in 1..2, "seed $seed: $a to $b") }
        }
    }

    @Test
    fun level14ReachesPastG4InMostExercises() {
        val reaching =
            (0L until SEEDS).count { seed ->
                SightReadingGenerator.passage(14, seed).steps.any {
                    it.notes
                        .single()
                        .pitch.number > 67
                }
            }
        assertTrue(reaching >= SEEDS * 9 / 10, "only $reaching of $SEEDS climb past G4")
    }

    @Test
    fun level14FillsFourFullBarsWithQuartersAndHalvesOnly() {
        for (seed in 0L until SEEDS) {
            val passage = SightReadingGenerator.passage(14, seed)
            val bar = passage.timeSignature.barTicks
            val allowed = listOf(TICKS_PER_QUARTER, 2 * TICKS_PER_QUARTER)
            assertTrue(passage.steps.all { step -> step.notes.single().lengthTicks in allowed }, "seed $seed")
            bars(passage).forEach { (n, steps) ->
                assertEquals(n * bar, steps.first().onsetTicks, "seed $seed bar $n starts on beat 1")
                val played = steps.sumOf { it.notes.single().lengthTicks }
                assertEquals(bar, played, "seed $seed bar $n")
            }
        }
    }

    @Test
    fun level14IsRebuiltFromItsId() {
        assertEquals(SightReadingGenerator.passage(14, 9), SightReadingGenerator.fromId("sight:14:9"))
        assertEquals(14 to 9L, SightReadingGenerator.parse("sight:14:9"))
    }

    @Test
    fun idRoundTrips() {
        assertEquals(3 to 42L, SightReadingGenerator.parse(SightReadingGenerator.idOf(3, 42)))
        assertEquals(2 to -5L, SightReadingGenerator.parse(SightReadingGenerator.idOf(2, -5)))
        assertEquals("sight:3:42", SightReadingGenerator.passage(3, 42).id)
    }

    @Test
    fun parseRejectsOtherIds() {
        listOf("ode-to-joy", "sight:x:1", "sight:15:1", "sight:0:1", "sight:1:y", "sight:1", "song:1:1", "sight:1:2:3")
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
        assertFailsWith<IllegalArgumentException> { SightReadingGenerator.passage(15, 1) }
    }
}
