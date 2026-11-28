package pianoanchor.feature.today

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Release
import pianoanchor.core.model.Settings
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.core.ports.UpdateCheckPort
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.testing.FakeWallClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The opt-out update check on Today (ADR 0004). */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayUpdateCheckTest : TodayViewModelFixture() {
    @Test
    fun `checks at most once per day`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            viewModel()
            viewModel()
            assertEquals(1, updateCheck.calls)
            assertEquals(today, settings.settings.first().lastUpdateCheck)

            clock.advance(1.days)
            val tomorrow = viewModel()
            assertEquals(2, updateCheck.calls)
            assertEquals(RELEASE_020, tomorrow.state.value.update)
        }

    @Test
    fun `a day starts at the learner day, not at midnight`() =
        runTest(dispatcher) {
            val twoAm = FakeWallClock(Instant.parse("2026-11-19T02:00:00Z"))
            settings.update { it.copy(lastUpdateCheck = today) }
            TodayViewModel(
                LearnerModel(Curriculum.graph, learnerRepository),
                settings,
                attempts,
                piano,
                piano,
                calibration,
                twoAm,
                TimeZone.UTC,
                updateCheck,
                INSTALLED,
                SessionProgress(settings),
            )
            runCurrent()
            assertEquals(0, updateCheck.calls)
        }

    @Test
    fun `a request cut short by leaving Today gives the day back`() =
        runTest(dispatcher) {
            val yesterday = LocalDate(2026, 11, 17)
            settings.update { it.copy(lastUpdateCheck = yesterday) }
            val leaving =
                object : UpdateCheckPort {
                    override suspend fun latest(): Release? = throw CancellationException("left Today")
                }
            TodayViewModel(
                LearnerModel(Curriculum.graph, learnerRepository),
                settings,
                attempts,
                piano,
                piano,
                calibration,
                clock,
                TimeZone.UTC,
                leaving,
                INSTALLED,
                SessionProgress(settings),
            )
            runCurrent()
            assertEquals(yesterday, settings.settings.first().lastUpdateCheck)

            updateCheck.release = RELEASE_020
            assertEquals(RELEASE_020, viewModel().state.value.update)
            assertEquals(1, updateCheck.calls)
        }

    @Test
    fun `a day claimed by another Today screen meanwhile is not asked again`() =
        runTest(dispatcher) {
            settings.update { it.copy(lastUpdateCheck = today) }
            // This screen's first read is stale (the day looks unchecked); the claiming update sees the real store.
            val staleRead =
                object : SettingsRepositoryPort by settings {
                    override val settings: Flow<Settings> = flowOf(Settings(weeklyGoalDays = 4))
                }
            TodayViewModel(
                LearnerModel(Curriculum.graph, learnerRepository),
                staleRead,
                attempts,
                piano,
                piano,
                calibration,
                clock,
                TimeZone.UTC,
                updateCheck,
                INSTALLED,
                SessionProgress(settings),
            )
            runCurrent()
            assertEquals(0, updateCheck.calls)
            assertEquals(today, settings.settings.first().lastUpdateCheck)
        }

    @Test
    fun `no request when the setting is off`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            settings.update { it.copy(updateCheckOn = false, latestRelease = RELEASE_020) }
            val vm = viewModel()
            assertEquals(0, updateCheck.calls)
            assertNull(vm.state.value.update)
        }

    @Test
    fun `banner when newer`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            assertEquals(RELEASE_020, viewModel().state.value.update)
            // Reopened the same day: no request, the stored release still shows.
            assertEquals(RELEASE_020, viewModel().state.value.update)
            assertEquals(1, updateCheck.calls)
        }

    @Test
    fun `no banner when the release is not newer`() =
        runTest(dispatcher) {
            updateCheck.release = Release(INSTALLED, RELEASE_020.pageUrl)
            assertNull(viewModel().state.value.update)
        }

    @Test
    fun `not now hides until a newer version`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            val vm = viewModel()
            vm.dismissUpdate()
            runCurrent()
            assertNull(vm.state.value.update)
            assertEquals("0.2.0", settings.settings.first().dismissedUpdate)
            assertNull(viewModel().state.value.update)

            clock.advance(1.days)
            updateCheck.release = Release("0.2.1", RELEASE_020.pageUrl)
            assertEquals(
                "0.2.1",
                viewModel()
                    .state.value.update
                    ?.version,
            )
        }

    @Test
    fun `dismissing with no banner does nothing`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.dismissUpdate()
            runCurrent()
            assertNull(settings.settings.first().dismissedUpdate)
        }

    @Test
    fun `failure shows nothing`() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertNull(vm.state.value.update)
            assertFalse(vm.state.value.storageFailed)
            assertEquals(1, updateCheck.calls)
        }

    @Test
    fun `a settings store that fails shows no banner and no update error`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            settings.failing = true
            val vm = viewModel()
            assertNull(vm.state.value.update)
            assertEquals(0, updateCheck.calls)
        }

    @Test
    fun `dismissing while the store fails still hides the banner`() =
        runTest(dispatcher) {
            updateCheck.release = RELEASE_020
            val vm = viewModel()
            settings.failing = true
            vm.dismissUpdate()
            runCurrent()
            assertNull(vm.state.value.update)
        }
}
