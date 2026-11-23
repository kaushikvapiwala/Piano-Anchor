package pianoanchor.engine.lesson

import pianoanchor.core.model.Rating
import pianoanchor.core.model.TryCheck
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuizMakerTest {
    private val everything =
        Curriculum.graph.skills
            .map { it.id }
            .toSet()

    private fun make(
        id: String,
        taught: Set<String> = everything,
        seed: Int = 1,
    ) = assertNotNull(QuizMaker.make(id, taught, Random(seed)))

    private fun every(id: String): List<Quiz> = (0..40).map { make(id, seed = it) }

    @Test
    fun `middle c and black key groups ask for an unlit middle c`() {
        for (skill in listOf(Skills.MIDDLE_C, Skills.BLACK_KEY_GROUPS)) {
            val quiz = make(skill.id)
            assertEquals(QuizPrompt.PlayKey(60), quiz.prompt)
            assertEquals(TryCheck.PlayKey(60, lit = false), quiz.check)
            assertEquals(skill.id, quiz.skillId)
        }
    }

    @Test
    fun `high and low asks for every C in the quiz range`() {
        val quiz = make(Skills.HIGH_AND_LOW.id)
        assertEquals(QuizPrompt.PlayEvery(0), quiz.prompt)
        assertEquals(TryCheck.FindAll(0, Tunables.QUIZ_RANGE), quiz.check)
    }

    @Test
    fun `play every quizzes have three or four targets`() {
        for (quiz in every(Skills.WHITE_KEY_NAMES.id)) {
            val find = assertIs<TryCheck.FindAll>(quiz.check)
            assertTrue(find.range.count { it % 12 == find.pitchClass } in 3..4)
        }
    }

    @Test
    fun `white key names picks any of the seven letters even before others are taught`() {
        val letters =
            (0..60).map {
                assertIs<QuizPrompt.PlayEvery>(make(Skills.WHITE_KEY_NAMES.id, emptySet(), it).prompt).pitchClass
            }
        assertEquals(setOf(0, 2, 4, 5, 7, 9, 11), letters.toSet())
    }

    @Test
    fun `the beat and note length skills`() {
        assertEquals(TryCheck.Repeat(60, 4, 60), make(Skills.STEADY_BEAT.id).check)
        assertEquals(QuizPrompt.ClickTimes(60, 4), make(Skills.STEADY_BEAT.id).prompt)
        assertEquals(TryCheck.Hold(60, 2, 60), make(Skills.QUARTER_AND_HALF.id).check)
        assertEquals(QuizPrompt.HoldFor(60, 2), make(Skills.QUARTER_AND_HALF.id).prompt)
        assertEquals(TryCheck.Hold(48, 4, 60), make(Skills.BASS_CLEF_WHOLE_NOTE.id).check)
        assertEquals(QuizPrompt.HoldFor(48, 4), make(Skills.BASS_CLEF_WHOLE_NOTE.id).prompt)
        assertEquals(TryCheck.StressBeatOne(60, 1, 60), make(Skills.BARS_AND_COUNTING.id).check)
        assertEquals(QuizPrompt.AccentBeatOne(60), make(Skills.BARS_AND_COUNTING.id).prompt)
    }

    @Test
    fun `right hand position steps up from a white key`() {
        val starts = every(Skills.RH_C_POSITION.id).map { assertIs<QuizPrompt.StepUpFrom>(it.prompt).note }
        assertEquals(setOf(60, 62, 64, 65), starts.toSet())
        val fromE = every(Skills.RH_C_POSITION.id).first { (it.prompt as QuizPrompt.StepUpFrom).note == 64 }
        assertEquals(TryCheck.Sequence(listOf(64, 65)), fromE.check)
        val quiz = every(Skills.RH_C_POSITION.id).first { (it.prompt as QuizPrompt.StepUpFrom).note == 65 }
        assertEquals(TryCheck.Sequence(listOf(65, 67)), quiz.check)
    }

    @Test
    fun `steps and skips skips two white keys up`() {
        val quizzes = every(Skills.STEPS_AND_SKIPS.id)
        assertEquals(setOf(60, 62, 64), quizzes.map { (it.prompt as QuizPrompt.SkipUpFrom).note }.toSet())
        assertEquals(
            TryCheck.Sequence(listOf(64, 67)),
            quizzes.first { (it.prompt as QuizPrompt.SkipUpFrom).note == 64 }.check,
        )
        assertEquals(
            TryCheck.Sequence(listOf(60, 64)),
            quizzes.first { (it.prompt as QuizPrompt.SkipUpFrom).note == 60 }.check,
        )
    }

    @Test
    fun `staff skills show the key on a mini staff`() {
        val steps = every(Skills.STAFF_STEPS.id)
        assertEquals(
            (60..67)
                .filter {
                    it % 12 in setOf(0, 2, 4, 5, 7, 9, 11)
                }.toSet(),
            steps.map { (it.prompt as QuizPrompt.PlayKey).note }.toSet(),
        )
        assertTrue(steps.all { (it.prompt as QuizPrompt.PlayKey).onStaff })
        assertTrue(steps.all { (it.check as TryCheck.PlayKey).let { check -> !check.lit } })
        val treble = make(Skills.TREBLE_CLEF.id)
        assertEquals(QuizPrompt.PlayKey(67, onStaff = true), treble.prompt)
        assertEquals(TryCheck.PlayKey(67, lit = false), treble.check)
    }

    @Test
    fun `soft and loud and the left hand position pick white keys`() {
        assertEquals(
            setOf(60, 62, 64, 65, 67),
            every(Skills.SOFT_AND_LOUD.id)
                .map {
                    (it.prompt as QuizPrompt.SoftThenLoud).note
                }.toSet(),
        )
        assertTrue(
            every(Skills.SOFT_AND_LOUD.id).all {
                it.check ==
                    TryCheck.SoftThenLoud((it.prompt as QuizPrompt.SoftThenLoud).note)
            },
        )
        val lh = every(Skills.LH_C_POSITION.id)
        assertEquals(setOf(48, 50, 52, 53, 55), lh.map { (it.prompt as QuizPrompt.PlayKey).note }.toSet())
        assertTrue(lh.all { (it.check as TryCheck.PlayKey).note == (it.prompt as QuizPrompt.PlayKey).note })
    }

    @Test
    fun `without the white key names only middle c and the c below are asked`() {
        val none = emptySet<String>()
        for (seed in 0..20) {
            assertEquals(QuizPrompt.StepUpFrom(60), QuizMaker.make(Skills.RH_C_POSITION.id, none, Random(seed))?.prompt)
            assertEquals(
                QuizPrompt.SkipUpFrom(60),
                QuizMaker.make(Skills.STEPS_AND_SKIPS.id, none, Random(seed))?.prompt,
            )
            assertEquals(QuizPrompt.PlayKey(48), QuizMaker.make(Skills.LH_C_POSITION.id, none, Random(seed))?.prompt)
            assertEquals(
                QuizPrompt.SoftThenLoud(60),
                QuizMaker.make(Skills.SOFT_AND_LOUD.id, none, Random(seed))?.prompt,
            )
            assertEquals(
                QuizPrompt.PlayKey(60, onStaff = true),
                QuizMaker.make(Skills.STAFF_STEPS.id, none, Random(seed))?.prompt,
            )
        }
    }

    @Test
    fun `chords`() {
        val c = make(Skills.C_CHORD.id)
        assertEquals(QuizPrompt.ChordOf(ChordName.C), c.prompt)
        assertEquals(TryCheck.Chord(setOf(60, 64, 67)), c.check)
        val all = every(Skills.G_AND_F_CHORDS.id)
        assertEquals(ChordName.entries.toSet(), all.map { (it.prompt as QuizPrompt.ChordOf).name }.toSet())
        assertEquals(
            TryCheck.Chord(setOf(55, 59, 62)),
            all.first { it.prompt == QuizPrompt.ChordOf(ChordName.G) }.check,
        )
        assertEquals(
            TryCheck.Chord(setOf(53, 57, 60)),
            all.first { it.prompt == QuizPrompt.ChordOf(ChordName.F) }.check,
        )
        // The C chord is only asked once it was taught.
        val before =
            (0..40).map {
                (QuizMaker.make(Skills.G_AND_F_CHORDS.id, emptySet(), Random(it))?.prompt as QuizPrompt.ChordOf).name
            }
        assertEquals(setOf(ChordName.G, ChordName.F), before.toSet())
    }

    @Test
    fun `sharps and flats name a black key with either sign`() {
        val prompts = every(Skills.SHARPS_AND_FLATS.id).map { it.prompt as QuizPrompt.PlayKey }
        assertEquals(setOf(61, 63, 66, 68, 70), prompts.map { it.note }.toSet())
        assertEquals(setOf(true, false), prompts.map { it.flat }.toSet())
        assertTrue(prompts.none { it.onStaff })
        assertEquals(
            TryCheck.PlayKey(prompts.first().note, lit = false),
            every(Skills.SHARPS_AND_FLATS.id).first().check,
        )
    }

    @Test
    fun `the same seed gives the same quiz`() {
        for (skill in Curriculum.graph.skills) {
            assertEquals(
                QuizMaker.make(skill.id, everything, Random(7)),
                QuizMaker.make(skill.id, everything, Random(7)),
            )
        }
    }

    @Test
    fun `passage skills and hand shape have no quiz`() {
        for (skill in listOf(
            Skills.HAND_SHAPE,
            Skills.FINGER_NUMBER_TUNES,
            Skills.GRAND_STAFF_TURNS_RESTS,
            Skills.THREE_FOUR_TIME,
            Skills.HANDS_TOGETHER_HELD,
            Skills.MELODY_AND_CHORDS,
        )) {
            assertNull(QuizMaker.make(skill.id, everything, Random(1)), skill.id)
        }
    }

    @Test
    fun `ratings`() {
        assertEquals(Rating.GOOD, QuizMaker.rating(0, skipped = false))
        assertEquals(Rating.HARD, QuizMaker.rating(2, skipped = false))
        assertEquals(Rating.AGAIN, QuizMaker.rating(0, skipped = true))
        assertEquals(Rating.AGAIN, QuizMaker.rating(5, skipped = true))
    }
}
