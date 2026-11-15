package pianoanchor.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.Release
import pianoanchor.core.model.Settings
import pianoanchor.core.model.Strictness
import pianoanchor.core.ports.SettingsRepositoryPort
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CONCURRENT_INCREMENTS = 20
private const val DAYS_IN_WEEK = 7
private const val TWO_INCREMENTS_FROM_ONE = 3

/** Behaviour every [SettingsRepositoryPort] adapter must satisfy. Subclass it once per adapter. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class SettingsRepositoryPortContractTest {
    protected abstract fun createSubject(): SettingsRepositoryPort

    @Test
    fun readsTheDefaultsBeforeAnyUpdate() =
        runTest {
            assertEquals(Settings(), createSubject().settings.first())
        }

    @Test
    fun anUpdateIsStoredAndEmitted() =
        runTest {
            val repository = createSubject()
            repository.update {
                it.copy(
                    dailyBudget = DailyBudget.MIN_30,
                    weeklyGoalDays = 6,
                    clickOn = false,
                    onboarded = true,
                )
            }
            val expected =
                Settings(
                    DailyBudget.MIN_30,
                    weeklyGoalDays = 6,
                    clickOn = false,
                    updateCheckOn = true,
                    onboarded = true,
                )
            assertEquals(expected, repository.settings.first())
            repository.update { it.copy(updateCheckOn = false) }
            assertEquals(expected.copy(updateCheckOn = false), repository.settings.first())
        }

    @Test
    fun strictnessIsSavedAndReadBack() =
        runTest {
            val repository = createSubject()
            assertEquals(Strictness.STANDARD, repository.settings.first().strictness)
            repository.update { it.copy(strictness = Strictness.STRICT) }
            assertEquals(Strictness.STRICT, repository.settings.first().strictness)
            repository.update { it.copy(strictness = Strictness.GENTLE) }
            assertEquals(Strictness.GENTLE, repository.settings.first().strictness)
        }

    @Test
    fun theUpdateCheckStateIsStoredAndCleared() =
        runTest {
            val repository = createSubject()
            val state =
                Settings(
                    lastUpdateCheck = LocalDate(2026, 11, 18),
                    latestRelease = Release("0.2.0", "https://github.com/o/r/releases/tag/v0.2.0"),
                    dismissedUpdate = "0.2.0",
                )
            repository.update { it.copy(lastUpdateCheck = state.lastUpdateCheck, latestRelease = state.latestRelease) }
            repository.update { it.copy(dismissedUpdate = "0.2.0") }
            assertEquals(state, repository.settings.first())
            repository.update { it.copy(lastUpdateCheck = null, latestRelease = null, dismissedUpdate = null) }
            assertEquals(Settings(), repository.settings.first())
        }

    @Test
    fun updatesApplyAtomicallyInOrder() =
        runTest {
            val repository = createSubject()
            repository.update { it.copy(weeklyGoalDays = 1) }
            List(CONCURRENT_INCREMENTS) {
                async { repository.update { it.copy(weeklyGoalDays = it.weeklyGoalDays % DAYS_IN_WEEK + 1) } }
            }.awaitAll()
            // 1 + 20 steps around the cycle 1..7 (a lost update would land elsewhere).
            assertEquals(1 + CONCURRENT_INCREMENTS % DAYS_IN_WEEK, repository.settings.first().weeklyGoalDays)
        }

    @Test
    fun twoConcurrentIncrementsBothLand() =
        runTest {
            val repository = createSubject()
            repository.update { it.copy(weeklyGoalDays = 1) }
            listOf(
                async { repository.update { it.copy(weeklyGoalDays = it.weeklyGoalDays + 1) } },
                async { repository.update { it.copy(weeklyGoalDays = it.weeklyGoalDays + 1) } },
            ).awaitAll()
            assertEquals(TWO_INCREMENTS_FROM_ONE, repository.settings.first().weeklyGoalDays)
        }

    @Test
    fun emitsAfterEachUpdate() =
        runTest {
            val repository = createSubject()
            val seen = mutableListOf<Settings>()
            val collector =
                launch(UnconfinedTestDispatcher(testScheduler)) { repository.settings.take(3).toList(seen) }
            repository.update { it.copy(clickOn = false) }
            repository.update { it.copy(weeklyGoalDays = 2) }
            collector.join()
            assertEquals(
                listOf(Settings(), Settings(clickOn = false), Settings(clickOn = false, weeklyGoalDays = 2)),
                seen,
            )
        }
}
