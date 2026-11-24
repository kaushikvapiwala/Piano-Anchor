package pianoanchor.engine.sight

import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Passage
import pianoanchor.core.model.ScoreNote
import pianoanchor.core.model.Step
import pianoanchor.core.model.TICKS_PER_QUARTER
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.FOUR_FOUR
import pianoanchor.engine.content.THREE_FOUR
import pianoanchor.engine.content.exerciseRights
import kotlin.math.abs
import kotlin.random.Random

private const val ID_PREFIX = "sight"
private const val ID_PARTS = 3
private const val LEFT_HAND_LEVEL = 4
private const val TURNS_LEVEL = 5
private const val THREE_FOUR_LEVEL = 6
private const val HELD_LEVEL = 7
private const val CHORD_LEVEL = 8
private const val SKIPS_FROM_LEVEL = 2
private const val OCTAVE = 12
private const val DOTTED_HALF_QUARTERS = 3
private const val WHOLE_QUARTERS = 4
private const val MAX_STEP = 1
private const val MAX_SKIP = 2
private const val FIRST_CHORD_BAR = 0

private const val QUARTER = TICKS_PER_QUARTER
private const val HALF = 2 * TICKS_PER_QUARTER
private const val DOTTED_HALF = DOTTED_HALF_QUARTERS * TICKS_PER_QUARTER
private const val WHOLE = WHOLE_QUARTERS * TICKS_PER_QUARTER

/** Five white keys from C: right hand C4 to G4, left hand C3 to G3. */
private val RIGHT_HAND = listOf(60, 62, 64, 65, 67)
private val LEFT_HAND = listOf(48, 50, 52, 53, 55)
private val NOTE_INDICES = RIGHT_HAND.indices

/** A negative length is a rest. Each cell fills one bar. */
private val CELLS_LEVEL_1 =
    listOf(
        listOf(QUARTER, QUARTER, QUARTER, QUARTER),
        listOf(HALF, QUARTER, QUARTER),
        listOf(QUARTER, QUARTER, HALF),
        listOf(HALF, HALF),
    )
private val CELLS_LEVEL_3 =
    CELLS_LEVEL_1 +
        listOf(
            listOf(WHOLE),
            listOf(QUARTER, QUARTER, QUARTER, -QUARTER),
            listOf(HALF, QUARTER, -QUARTER),
        )
private val CELLS_THREE_FOUR =
    listOf(
        listOf(QUARTER, QUARTER, QUARTER),
        listOf(HALF, QUARTER),
        listOf(QUARTER, HALF),
        listOf(DOTTED_HALF),
        listOf(QUARTER, QUARTER, -QUARTER),
    )

/** The left-hand triads of level 8 as note numbers, near C3. */
private val C_TRIAD = listOf(48, 52, 55)
private val F_TRIAD = listOf(48, 53, 57)
private val G_TRIAD = listOf(47, 50, 55)
private val MIDDLE_CHORDS = listOf(F_TRIAD, G_TRIAD)

/** The held left-hand notes of level 7. */
private val HELD_NOTES = listOf(48, 55)

/** One note to place: when, how long, in which bar, and whether it is on beat 1. */
private class Slot(
    val onset: Int,
    val length: Int,
    val bar: Int,
    val first: Boolean,
)

/** Draws sight-reading exercises (`docs/algorithms/sight-reading.md`). Pure over a seeded [Random]. */
public object SightReadingGenerator {
    /**
     * The exercise for [level] (1..8) and [seed]: id "sight:<level>:<seed>", SIGHT_BARS bars, rights ORIGINAL,
     * skills empty, piece false. Same inputs give an equal [Passage]. Throws [IllegalArgumentException] for a level
     * outside 1..8.
     */
    public fun passage(
        level: Int,
        seed: Long,
    ): Passage {
        require(level in 1..SightLevels.all.size) { "sight level must be in 1..${SightLevels.all.size}, was $level" }
        val random = Random(seed)
        val signature = if (level == THREE_FOUR_LEVEL) THREE_FOUR else FOUR_FOUR
        val held = heldNotes(level, random)
        val slots = slots(level, signature.barTicks, random)
        val chordClasses =
            if (level ==
                CHORD_LEVEL
            ) {
                held.map { chord -> chord.map { it % OCTAVE }.toSet() }
            } else {
                emptyList()
            }
        val indices = walk(slots, chordClasses, if (level == 1) MAX_STEP else MAX_SKIP, random)
        val steps =
            slots.zip(indices).map { (slot, index) ->
                val notes = mutableSetOf(ScoreNote(MidiNote(hand(level, slot.bar)[index]), slot.length))
                if (slot.first && held.isNotEmpty()) {
                    held[slot.bar].forEach { notes += ScoreNote(MidiNote(it), signature.barTicks) }
                }
                Step(slot.onset, notes)
            }
        return Passage(
            id = idOf(level, seed),
            title = "Sight-reading level $level",
            steps = steps,
            timeSignature = signature,
            tempoBpm = Tunables.SIGHT_TEMPO_BPM,
            rights = exerciseRights("sight-reading"),
        )
    }

    public fun idOf(
        level: Int,
        seed: Long,
    ): String = "$ID_PREFIX:$level:$seed"

    /** level and seed of a "sight:<level>:<seed>" id; null for any other id. */
    public fun parse(id: String): Pair<Int, Long>? {
        val parts = id.split(':')
        val level = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..SightLevels.all.size }
        val seed = parts.getOrNull(2)?.toLongOrNull()
        val sight = parts.size == ID_PARTS && parts[0] == ID_PREFIX
        return if (sight && level != null && seed != null) level to seed else null
    }

    /** The keys that play the tune in [bar]: level 4 is left; 5 and 6 alternate, starting right; the rest right. */
    private fun hand(
        level: Int,
        bar: Int,
    ) = when {
        level == LEFT_HAND_LEVEL -> LEFT_HAND
        level == TURNS_LEVEL || level == THREE_FOUR_LEVEL -> if (bar % 2 == 0) RIGHT_HAND else LEFT_HAND
        else -> RIGHT_HAND
    }

    /** The left-hand notes held through each bar at levels 7 (one note) and 8 (a triad); empty before. */
    private fun heldNotes(
        level: Int,
        random: Random,
    ): List<List<Int>> =
        when (level) {
            HELD_LEVEL -> {
                List(
                    Tunables.SIGHT_BARS,
                ) { bar -> listOf(if (isEdge(bar)) HELD_NOTES[0] else HELD_NOTES.random(random)) }
            }

            CHORD_LEVEL -> {
                List(
                    Tunables.SIGHT_BARS,
                ) { bar -> if (isEdge(bar)) C_TRIAD else MIDDLE_CHORDS.random(random) }
            }

            else -> {
                emptyList()
            }
        }

    /** The first and last bar are on C. */
    private fun isEdge(bar: Int) = bar == FIRST_CHORD_BAR || bar == Tunables.SIGHT_BARS - 1

    private fun cells(level: Int) =
        when (level) {
            1, 2 -> CELLS_LEVEL_1
            THREE_FOUR_LEVEL -> CELLS_THREE_FOUR
            else -> CELLS_LEVEL_3
        }

    /** Fills each bar from a rhythm cell; the last bar takes one with no rest whose last note is its longest. */
    private fun slots(
        level: Int,
        barTicks: Int,
        random: Random,
    ): List<Slot> =
        (0 until Tunables.SIGHT_BARS).flatMap { bar ->
            val all = cells(level)
            val pool =
                if (bar == Tunables.SIGHT_BARS - 1) {
                    all.filter { cell -> cell.none { it < 0 } && cell.last() == cell.max() }
                } else {
                    all
                }
            var onset = bar * barTicks
            pool
                .random(random)
                .map { length ->
                    Slot(onset, length, bar, onset == bar * barTicks).also { onset += abs(length) }
                }.filter { it.length > 0 }
        }

    /**
     * The key index (0..4) of every slot: a random walk from C by the allowed jumps (it may stay put) that ends on C
     * and, when [chordClasses] is given, puts each bar's beat-1 note in that bar's chord. Backtracks out of dead ends.
     */
    private fun walk(
        slots: List<Slot>,
        chordClasses: List<Set<Int>>,
        maxJump: Int,
        random: Random,
    ): List<Int> {
        fun fits(
            slot: Int,
            index: Int,
        ): Boolean {
            val s = slots[slot]
            val onC = (slot != 0 && slot != slots.lastIndex) || index == 0
            return onC && (!s.first || chordClasses.isEmpty() || RIGHT_HAND[index] % OCTAVE in chordClasses[s.bar])
        }

        fun extend(path: List<Int>): List<Int> =
            if (path.size == slots.size) {
                path
            } else {
                NOTE_INDICES
                    .filter { path.isEmpty() || abs(it - path.last()) <= maxJump }
                    .filter { fits(path.size, it) }
                    .shuffled(random)
                    .asSequence()
                    .map { extend(path + it) }
                    .firstOrNull { it.isNotEmpty() } ?: emptyList()
            }
        return extend(emptyList())
    }
}
