package pianoanchor.engine.lesson

import pianoanchor.core.model.Rating
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Skills
import kotlin.random.Random

private const val NOTES_PER_OCTAVE = 12
private const val MIDDLE_C = 60
private const val C_BELOW_MIDDLE_C = 48
private const val G_ABOVE_MIDDLE_C = 67
private const val QUIZ_BPM = 60
private const val HOLD_HALF_CLICKS = 2
private const val HOLD_WHOLE_CLICKS = 4
private const val CLICK_TIMES = 4
private const val ACCENT_BARS = 1
private const val F_ABOVE_MIDDLE_C = 65
private const val E_ABOVE_MIDDLE_C = 64
private const val G_BELOW_MIDDLE_C = 55
private const val B_BELOW_MIDDLE_C = 59
private const val D_ABOVE_MIDDLE_C = 62
private const val F_BELOW_MIDDLE_C = 53
private const val A_BELOW_MIDDLE_C = 57
private const val SKIP_WHITE_KEYS = 2
private val WHITE_PITCH_CLASSES = setOf(0, 2, 4, 5, 7, 9, 11)

/** The black keys from C♯4 to A♯4. */
private val BLACK_KEYS = listOf(61, 63, 66, 68, 70)

/** The quizzes that never vary. */
private val FIXED: Map<String, QuizPrompt> =
    mapOf(
        Skills.MIDDLE_C.id to QuizPrompt.PlayKey(MIDDLE_C),
        Skills.BLACK_KEY_GROUPS.id to QuizPrompt.PlayKey(MIDDLE_C),
        Skills.HIGH_AND_LOW.id to QuizPrompt.PlayEvery(0),
        Skills.STEADY_BEAT.id to QuizPrompt.ClickTimes(MIDDLE_C, CLICK_TIMES),
        Skills.QUARTER_AND_HALF.id to QuizPrompt.HoldFor(MIDDLE_C, HOLD_HALF_CLICKS),
        Skills.BASS_CLEF_WHOLE_NOTE.id to QuizPrompt.HoldFor(C_BELOW_MIDDLE_C, HOLD_WHOLE_CLICKS),
        Skills.BARS_AND_COUNTING.id to QuizPrompt.AccentBeatOne(MIDDLE_C),
        Skills.TREBLE_CLEF.id to QuizPrompt.PlayKey(G_ABOVE_MIDDLE_C, onStaff = true),
        Skills.C_CHORD.id to QuizPrompt.ChordOf(ChordName.C),
    )

/** The three chords of the course. */
public enum class ChordName { C, G, F }

/** What a quiz asks; the screen words each one (the strings live with the screen). */
public sealed interface QuizPrompt {
    /** Play every key of [pitchClass] (0 = C ... 11 = B). */
    public data class PlayEvery(
        val pitchClass: Int,
    ) : QuizPrompt

    /**
     * Play [note]. [onStaff] shows it as a note on a mini staff; [flat] names a black key with a flat instead of a
     * sharp.
     */
    public data class PlayKey(
        val note: Int,
        val onStaff: Boolean = false,
        val flat: Boolean = false,
    ) : QuizPrompt

    /** Play the key a skip up from [note] (two white keys on), after [note]. */
    public data class SkipUpFrom(
        val note: Int,
    ) : QuizPrompt

    /** Play the key a step up from [note] (the next white key), after [note]. */
    public data class StepUpFrom(
        val note: Int,
    ) : QuizPrompt

    /** Hold [note] for [clicks] clicks. */
    public data class HoldFor(
        val note: Int,
        val clicks: Int,
    ) : QuizPrompt

    /** Play [note] [times] times with the click. */
    public data class ClickTimes(
        val note: Int,
        val times: Int,
    ) : QuizPrompt

    /** Play [note] softly, then loudly. */
    public data class SoftThenLoud(
        val note: Int,
    ) : QuizPrompt

    /** Play [note] on every click, louder on beat 1. */
    public data class AccentBeatOne(
        val note: Int,
    ) : QuizPrompt

    /** Play the chord [name]. */
    public data class ChordOf(
        val name: ChordName,
    ) : QuizPrompt
}

/** One quiz: the [check] to pass for [skillId], and its instruction [prompt]. */
public data class Quiz(
    val skillId: String,
    val check: TryCheck,
    val prompt: QuizPrompt,
)

/** Makes the short review quizzes of Today's warm-up (`docs/algorithms/quizzes.md`). Pure. */
public object QuizMaker {
    /**
     * A quiz for [skillId] varied by [random] within what [taught] covers (plus the skill itself); null for skills
     * without one (passage concepts, hand-shape).
     */
    public fun make(
        skillId: String,
        taught: Set<String>,
        random: Random,
    ): Quiz? {
        val known = taught + skillId
        // Without the white-key names, only middle C and the Cs can be asked for by name.
        val named = Skills.WHITE_KEY_NAMES.id in known
        val prompt = prompt(skillId, known, named, random) ?: return null
        return Quiz(skillId, check(prompt), prompt)
    }

    /** The rating of a finished or skipped quiz: skipped AGAIN, any wrong key HARD, else GOOD. */
    public fun rating(
        wrongCount: Int,
        skipped: Boolean,
    ): Rating =
        when {
            skipped -> Rating.AGAIN
            wrongCount > 0 -> Rating.HARD
            else -> Rating.GOOD
        }

    private fun prompt(
        skillId: String,
        known: Set<String>,
        named: Boolean,
        random: Random,
    ): QuizPrompt? {
        /** A random white key of [range], or [fallback] while the names are not taught. */
        fun white(
            range: IntRange,
            fallback: Int,
        ): Int = if (named) range.filter { it % NOTES_PER_OCTAVE in WHITE_PITCH_CLASSES }.random(random) else fallback
        return FIXED[skillId] ?: when (skillId) {
            Skills.WHITE_KEY_NAMES.id -> {
                QuizPrompt.PlayEvery(WHITE_PITCH_CLASSES.random(random))
            }

            Skills.RH_C_POSITION.id -> {
                QuizPrompt.StepUpFrom(white(MIDDLE_C..F_ABOVE_MIDDLE_C, MIDDLE_C))
            }

            Skills.STEPS_AND_SKIPS.id -> {
                QuizPrompt.SkipUpFrom(white(MIDDLE_C..E_ABOVE_MIDDLE_C, MIDDLE_C))
            }

            Skills.STAFF_STEPS.id -> {
                QuizPrompt.PlayKey(white(MIDDLE_C..G_ABOVE_MIDDLE_C, MIDDLE_C), onStaff = true)
            }

            Skills.SOFT_AND_LOUD.id -> {
                QuizPrompt.SoftThenLoud(white(MIDDLE_C..G_ABOVE_MIDDLE_C, MIDDLE_C))
            }

            Skills.LH_C_POSITION.id -> {
                QuizPrompt.PlayKey(white(C_BELOW_MIDDLE_C..G_BELOW_MIDDLE_C, C_BELOW_MIDDLE_C))
            }

            Skills.G_AND_F_CHORDS.id -> {
                val chords = if (Skills.C_CHORD.id in known) ChordName.entries else listOf(ChordName.G, ChordName.F)
                QuizPrompt.ChordOf(chords.random(random))
            }

            Skills.SHARPS_AND_FLATS.id -> {
                QuizPrompt.PlayKey(BLACK_KEYS.random(random), flat = random.nextBoolean())
            }

            else -> {
                null
            }
        }
    }

    private fun check(prompt: QuizPrompt): TryCheck =
        when (prompt) {
            is QuizPrompt.PlayEvery -> TryCheck.FindAll(prompt.pitchClass, Tunables.QUIZ_RANGE)
            is QuizPrompt.PlayKey -> TryCheck.PlayKey(prompt.note, lit = false)
            is QuizPrompt.SkipUpFrom -> TryCheck.Sequence(listOf(prompt.note, whiteAbove(prompt.note, SKIP_WHITE_KEYS)))
            is QuizPrompt.StepUpFrom -> TryCheck.Sequence(listOf(prompt.note, whiteAbove(prompt.note, 1)))
            is QuizPrompt.HoldFor -> TryCheck.Hold(prompt.note, prompt.clicks, QUIZ_BPM)
            is QuizPrompt.ClickTimes -> TryCheck.Repeat(prompt.note, prompt.times, QUIZ_BPM)
            is QuizPrompt.SoftThenLoud -> TryCheck.SoftThenLoud(prompt.note)
            is QuizPrompt.AccentBeatOne -> TryCheck.StressBeatOne(prompt.note, ACCENT_BARS, QUIZ_BPM)
            is QuizPrompt.ChordOf -> TryCheck.Chord(chordNotes(prompt.name))
        }

    /** The white key [steps] white keys above the white key [note]. */
    private fun whiteAbove(
        note: Int,
        steps: Int,
    ): Int =
        generateSequence(note) { it + 1 }
            .filter { it % NOTES_PER_OCTAVE in WHITE_PITCH_CLASSES }
            .drop(steps)
            .first()

    private fun chordNotes(name: ChordName): Set<Int> =
        when (name) {
            ChordName.C -> setOf(MIDDLE_C, E_ABOVE_MIDDLE_C, G_ABOVE_MIDDLE_C)
            ChordName.G -> setOf(G_BELOW_MIDDLE_C, B_BELOW_MIDDLE_C, D_ABOVE_MIDDLE_C)
            ChordName.F -> setOf(F_BELOW_MIDDLE_C, A_BELOW_MIDDLE_C, MIDDLE_C)
        }
}
