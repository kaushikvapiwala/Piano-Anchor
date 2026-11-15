package pianoanchor.platform.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.Rating
import pianoanchor.core.model.Review
import pianoanchor.core.model.SkillState
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.LearnerRepositoryPort
import pianoanchor.testing.LearnerRepositoryPortContractTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private val DAY = LocalDate(2026, 11, 1)
private val REVIEW = Review("pitch:c4", DAY, Rating.GOOD, "a-1", unaided = true)
private val STATE =
    SkillState("pitch:c4", MemoryState(3.5, 6.25), DAY, DAY, LocalDate(2026, 11, 4), 1, 2, 1, false)

class SqlLearnerRepositoryAdapterTest : LearnerRepositoryPortContractTest() {
    private val drivers = InMemoryDrivers()

    override fun createSubject(): LearnerRepositoryPort =
        SqlLearnerRepositoryAdapter(drivers.open(), Dispatchers.Unconfined)

    @AfterTest
    fun closeDrivers() = drivers.close()

    @Test
    fun recordIsOneTransaction() =
        runTest {
            val driver = drivers.open()
            // A state write that always fails, as a full disk would, after the review row went in.
            driver.exec("CREATE TRIGGER no_state BEFORE INSERT ON skill_state BEGIN SELECT RAISE(ABORT, 'full'); END")
            val repository = SqlLearnerRepositoryAdapter(driver, Dispatchers.Unconfined)

            assertFailsWith<StorageUnavailableException> { repository.record(REVIEW, STATE) }

            assertEquals(0, driver.count("review"))
            assertEquals(0, driver.count("skill_state"))
        }

    @Test
    fun allMethodsThrowStorageUnavailableExceptionWhenTheDriverFails() =
        runTest {
            val driver = FailingDriver(drivers.open()).apply { failing = true }
            val repository = SqlLearnerRepositoryAdapter(driver, Dispatchers.Unconfined)

            assertFailsWith<StorageUnavailableException> { repository.record(REVIEW, STATE) }
            assertFailsWith<StorageUnavailableException> { repository.states() }
            assertFailsWith<StorageUnavailableException> { repository.reviews("pitch:c4") }
            assertFailsWith<StorageUnavailableException> { repository.replaceStates(listOf(STATE)) }
        }
}
