package pianoanchor.engine.session

import pianoanchor.core.model.RunMode
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.matcher.TempoRung
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionRunnerTest {
    private val passage = Curriculum.passages.first()
    private val skill = Skills.FINGER_NUMBER_TUNES

    private val cold = Card.ColdOpen(passage, TempoRung.WAIT, 4, skill, null, RunMode.PRACTICE)
    private val quizzes = Card.Quizzes(emptyList(), 2)
    private val lesson = Card.Lesson(passage, TempoRung.WAIT, 5, skill, emptyList())
    private val song = Card.Song(passage, TempoRung.WAIT, 6)
    private val sight = Card.SightReading(1, passage, 3)
    private val ear = Card.Ear(listOf(skill), 2)

    private fun session(vararg cards: Card) = Session(cards.toList(), 0, 0, 0, 0, emptyList())

    @Test
    fun `a full session runs warm-up, learn, practise, ear, sight-read`() {
        val steps = SessionRunner.steps(session(quizzes, cold, lesson, song, sight, ear))

        assertEquals(listOf(cold, quizzes, lesson, song, ear, sight), steps.map { it.card })
        assertIs<SessionStep.WarmUp>(steps[0])
        assertIs<SessionStep.WarmUp>(steps[1])
        assertIs<SessionStep.Learn>(steps[2])
        assertIs<SessionStep.Practise>(steps[3])
        assertIs<SessionStep.Ear>(steps[4])
        assertIs<SessionStep.SightRead>(steps[5])
        assertEquals(listOf(4, 2, 5, 6, 2, 3), steps.map { it.minutes })
    }

    @Test
    fun `skipped indices of the cards drop their steps`() {
        val steps = SessionRunner.steps(session(cold, lesson, song), skipped = setOf(1))

        assertEquals(listOf(cold, song), steps.map { it.card })
    }

    @Test
    fun `an empty session has no steps`() {
        assertTrue(SessionRunner.steps(session()).isEmpty())
    }

    @Test
    fun `a session without an ear card has no ear step`() {
        val steps = SessionRunner.steps(session(cold, lesson, song, sight))

        assertTrue(steps.none { it is SessionStep.Ear })
    }
}
