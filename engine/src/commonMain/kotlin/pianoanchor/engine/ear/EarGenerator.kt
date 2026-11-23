package pianoanchor.engine.ear

import pianoanchor.core.model.EarChoice
import pianoanchor.core.model.EarExercise
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Tone
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.EarSkills
import kotlin.random.Random

private const val C3 = 48
private const val G4 = 67
private const val C5 = 72
private const val MIN_LEAP = 2
private const val MAX_LEAP = 12
private const val MINOR_THIRD = 3
private const val MAJOR_THIRD = 4
private const val FOURTH = 5
private const val FIFTH = 7
private const val THREE_NOTES = 3
private const val FIVE_NOTES = 5

/** The white keys from C4 to C5 as note numbers. */
private val WHITE_KEYS = listOf(60, 62, 64, 65, 67, 69, 71, 72)

/** The white keys C4 to G4: the right hand's five-finger position. */
private val POSITION_KEYS = WHITE_KEYS.take(5)

private fun note(number: Int) = setOf(MidiNote(number))

/** Draws ear items for the ear skills (`docs/algorithms/ear-training.md`). Pure over an injected [Random]. */
public object EarGenerator {
    /** Draws one new item of the ear skill [skillId]. Throws [IllegalArgumentException] for any other skill. */
    public fun draw(
        skillId: String,
        random: Random,
    ): EarExercise =
        when (skillId) {
            EarSkills.ECHO_NOTE.id -> {
                echo(listOf(POSITION_KEYS.random(random)), exactOctave = true)
            }

            EarSkills.HIGHER_LOWER.id -> {
                higherLower(random)
            }

            EarSkills.STEP_SKIP.id -> {
                stepSkip(random)
            }

            EarSkills.ECHO_THREE.id -> {
                echo(melody(THREE_NOTES, random), exactOctave = false)
            }

            EarSkills.THIRDS.id -> {
                interval(
                    random,
                    EarChoice.MAJOR_THIRD to MAJOR_THIRD,
                    EarChoice.MINOR_THIRD to MINOR_THIRD,
                )
            }

            EarSkills.FOURTH_FIFTH.id -> {
                interval(random, EarChoice.FOURTH to FOURTH, EarChoice.FIFTH to FIFTH)
            }

            EarSkills.CHORD_QUALITY.id -> {
                chord(random)
            }

            EarSkills.ECHO_FIVE.id -> {
                echo(melody(FIVE_NOTES, random), exactOctave = false)
            }

            else -> {
                throw IllegalArgumentException("\"$skillId\" is not an ear skill")
            }
        }

    /**
     * Draws an item of [skillId] not in [drawn], trying EAR_MAX_REDRAWS draws before accepting one more draw as it is.
     */
    public fun drawNew(
        skillId: String,
        drawn: Collection<EarExercise>,
        random: Random,
    ): EarExercise =
        generateSequence { draw(skillId, random) }
            .take(Tunables.EAR_MAX_REDRAWS)
            .firstOrNull { it !in drawn } ?: draw(skillId, random)

    private fun echo(
        notes: List<Int>,
        exactOctave: Boolean,
    ) = EarExercise.Echo(notes.map { note(it) }, exactOctave)

    private fun higherLower(random: Random): EarExercise {
        val first = random.nextInt(C3, C5 + 1)
        val up = random.nextBoolean()
        val leap = random.nextInt(MIN_LEAP, MAX_LEAP + 1)
        // Flip the direction when the first note leaves no room that way (leaps of 2 to 12 always fit one way).
        val goUp = if (up) first + leap <= C5 else first - leap < C3
        val second = if (goUp) first + leap else first - leap
        return EarExercise.Identify(
            listOf(note(first), note(second)),
            listOf(EarChoice.HIGHER, EarChoice.LOWER),
            if (second > first) EarChoice.HIGHER else EarChoice.LOWER,
        )
    }

    private fun stepSkip(random: Random): EarExercise {
        val skip = random.nextBoolean()
        val size = if (skip) 2 else 1
        val pairs =
            WHITE_KEYS.indices.flatMap { i ->
                listOf(i - size, i + size).filter { it in WHITE_KEYS.indices }.map {
                    i to
                        it
                }
            }
        val (from, to) = pairs.random(random)
        return EarExercise.Identify(
            listOf(note(WHITE_KEYS[from]), note(WHITE_KEYS[to])),
            listOf(EarChoice.STEP, EarChoice.SKIP),
            if (skip) EarChoice.SKIP else EarChoice.STEP,
        )
    }

    /** [length] notes of C4 to G4, each a step or a skip from the one before, up or down. */
    private fun melody(
        length: Int,
        random: Random,
    ): List<Int> {
        var index = POSITION_KEYS.indices.random(random)
        val indices = arrayListOf(index)
        repeat(length - 1) {
            index =
                listOf(index - 2, index - 1, index + 1, index + 2).filter { it in POSITION_KEYS.indices }.random(random)
            indices += index
        }
        return indices.map { POSITION_KEYS[it] }
    }

    private fun interval(
        random: Random,
        first: Pair<EarChoice, Int>,
        second: Pair<EarChoice, Int>,
    ): EarExercise {
        val (answer, semitones) = if (random.nextBoolean()) first else second
        val root = random.nextInt(C3, G4 + 1)
        return EarExercise.Identify(
            listOf(note(root), note(root + semitones)),
            listOf(first.first, second.first),
            answer,
        )
    }

    private fun chord(random: Random): EarExercise {
        val major = random.nextBoolean()
        val root = random.nextInt(C3, G4 + 1)
        val third = if (major) MAJOR_THIRD else MINOR_THIRD
        return EarExercise.Identify(
            listOf(setOf(MidiNote(root), MidiNote(root + third), MidiNote(root + FIFTH))),
            listOf(EarChoice.MAJOR_CHORD, EarChoice.MINOR_CHORD),
            if (major) EarChoice.MAJOR_CHORD else EarChoice.MINOR_CHORD,
        )
    }
}

/**
 * The playback schedule of this item, shared by MIDI and speaker output: one group every PLAYBACK_NOTE_GAP_MS, a
 * single note held PLAYBACK_NOTE_LENGTH_MS and a chord EAR_CHORD_LENGTH_MS.
 */
public fun EarExercise.tones(): List<Tone> =
    sounds.withIndex().flatMap { (index, group) ->
        val length = if (group.size > 1) Tunables.EAR_CHORD_LENGTH_MS else Tunables.PLAYBACK_NOTE_LENGTH_MS
        group.sortedBy { it.number }.map { Tone(it, index * Tunables.PLAYBACK_NOTE_GAP_MS, length) }
    }
