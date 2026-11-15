package pianoanchor.platform.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.Settings
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.Strictness
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.testing.SettingsRepositoryPortContractTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlSettingsRepositoryAdapterTest : SettingsRepositoryPortContractTest() {
    private val drivers = InMemoryDrivers()

    override fun createSubject(): SettingsRepositoryPort =
        SqlSettingsRepositoryAdapter(drivers.open(), Dispatchers.Unconfined)

    @AfterTest
    fun closeDrivers() = drivers.close()

    @Test
    fun unreadableStoredValueFallsBackToTheDefault() =
        runTest {
            val driver = drivers.open()
            val repository = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined)
            driver.exec("INSERT INTO setting VALUES ('daily_budget', 'MIN_99'), ('weekly_goal_days', '9')")
            driver.exec(
                "INSERT INTO setting VALUES ('click_on', 'maybe'), ('update_check_on', 'false'), ('onboarded', 'yes')",
            )
            assertEquals(Settings(updateCheckOn = false), repository.settings.first())

            repository.update { it.copy(dailyBudget = DailyBudget.MIN_15) }

            assertEquals(Settings(DailyBudget.MIN_15, updateCheckOn = false), repository.settings.first())
            assertEquals(9, driver.count("setting"))
        }

    @Test
    fun unreadableUpdateCheckStateReadsAsNotSet() =
        runTest {
            val driver = drivers.open()
            driver.exec(
                "INSERT INTO setting VALUES ('last_update_check', 'yesterday'), ('latest_release', 'v1 a b'), " +
                    "('dismissed_update', '')",
            )
            assertEquals(Settings(), SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first())
            for (unreadable in listOf("v1", "v1 ", " https://x")) {
                driver.exec("UPDATE setting SET text_value = '$unreadable' WHERE key = 'latest_release'")
                assertEquals(Settings(), SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first())
            }
        }

    @Test
    fun anUnknownStoredStrictnessFallsBackToStandard() =
        runTest {
            val driver = drivers.open()
            driver.exec("INSERT INTO setting VALUES ('strictness', 'RELAXED')")

            val stored = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first()

            assertEquals(Strictness.STANDARD, stored.strictness)
        }

    @Test
    fun aNonNumericWeeklyGoalFallsBackToTheDefault() =
        runTest {
            val driver = drivers.open()
            driver.exec("INSERT INTO setting VALUES ('weekly_goal_days', 'many'), ('daily_budget', 'MIN_30')")

            val stored = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined).settings.first()

            assertEquals(Settings(DailyBudget.MIN_30), stored)
        }

    @Test
    fun readAndUpdateThrowStorageUnavailableExceptionWhenTheDriverFails() =
        runTest {
            val driver = FailingDriver(drivers.open()).apply { failing = true }
            val repository = SqlSettingsRepositoryAdapter(driver, Dispatchers.Unconfined)
            assertFailsWith<StorageUnavailableException> { repository.settings.first() }
            assertFailsWith<StorageUnavailableException> { repository.update { it } }
        }
}
