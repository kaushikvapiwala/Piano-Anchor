package pianoanchor.feature.today

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.Settings
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.SettingsRepositoryPort
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

    @Test
    fun `a mark whose write failed survives a read that succeeds without it`() =
        runTest {
            val flaky = FlakyWrites(settings)
            val progress = SessionProgress(flaky)
            flaky.failWrites = true

            progress.markFinished(day, step)

            assertEquals(setOf(step.key()), progress.finishedOn(day))
            flaky.failWrites = false
            progress.markCompleted(day)
            val restarted = SessionProgress(settings)
            assertEquals(setOf(step.key()), restarted.finishedOn(day))
            assertTrue(restarted.completedOn(day))
        }

    @Test
    fun `a recorded step is visible before it is flushed`() =
        runTest {
            val progress = SessionProgress(settings)

            progress.recordFinished(day, step)

            assertEquals(setOf(step.key()), progress.finishedOn(day))
        }

    @Test
    fun `restart forgets the day's steps and completion`() =
        runTest {
            val progress = SessionProgress(settings)
            progress.markFinished(day, step)
            progress.markCompleted(day)

            progress.restart(day)

            assertEquals(emptySet(), SessionProgress(settings).finishedOn(day))
            assertFalse(SessionProgress(settings).completedOn(day))
        }
}

/** Settings whose writes can be made to fail while reads still work. */
private class FlakyWrites(
    private val inner: FakeSettingsRepository,
) : SettingsRepositoryPort by inner {
    var failWrites = false

    override suspend fun update(change: (Settings) -> Settings) {
        if (failWrites) throw StorageUnavailableException("update failed")
        inner.update(change)
    }
}
