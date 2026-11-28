package pianoanchor.feature.today

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.SessionStep
import pianoanchor.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionProgressTest {
    private val settings = FakeSettingsRepository()
    private val day = LocalDate(2026, 11, 28)
    private val passage = checkNotNull(Curriculum.passage("builtin:middle-c-march"))
    private val step = SessionStep.Ear(Card.Ear(emptyList(), 2), 2)
    private val other = SessionStep.Practise(Card.Song(passage, TempoRung.WAIT, 6), 6)

    @Test
    fun `marks survive a restart`() =
        runTest {
            val progress = SessionProgress(settings)
            progress.markFinished(day, step)
            progress.markCompleted(day)

            val restarted = SessionProgress(settings)

            assertEquals(setOf(step.key()), restarted.finishedOn(day))
            assertTrue(restarted.completedOn(day))
        }

    @Test
    fun `nothing is finished or completed before a mark`() =
        runTest {
            val progress = SessionProgress(settings)

            assertEquals(emptySet(), progress.finishedOn(day))
            assertFalse(progress.completedOn(day))
        }

    @Test
    fun `a new day drops the old entries on its first write`() =
        runTest {
            val progress = SessionProgress(settings)
            progress.markFinished(day, step)
            progress.markCompleted(day)
            val next = LocalDate(2026, 11, 29)

            assertEquals(emptySet(), progress.finishedOn(next))
            assertFalse(progress.completedOn(next))
            progress.markFinished(next, other)

            assertEquals(setOf(other.key()), progress.finishedOn(next))
            assertFalse(progress.completedOn(next))
            assertEquals(emptySet(), progress.finishedOn(day))
        }

    @Test
    fun `a storage failure on write keeps the run going in memory`() =
        runTest {
            val progress = SessionProgress(settings)
            settings.failing = true

            progress.markFinished(day, step)
            progress.markCompleted(day)

            assertEquals(setOf(step.key()), progress.finishedOn(day))
            assertTrue(progress.completedOn(day))
            settings.failing = false
            assertEquals(emptySet(), SessionProgress(settings).finishedOn(day))
        }
}
