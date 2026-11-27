package pianoanchor.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.NoteNames
import pianoanchor.core.model.Settings
import pianoanchor.core.model.Strictness
import pianoanchor.testing.FakeSettingsRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeSettingsRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = SettingsViewModel(repository).also { runCurrent() }

    @Test
    fun showsTheDefaultsAtFirst() =
        runTest(dispatcher) {
            val vm = viewModel()

            assertEquals(SettingsState(Settings()), vm.state.value)
        }

    @Test
    fun showsTheStoredSettings() =
        runTest(dispatcher) {
            repository.update { it.copy(dailyBudget = DailyBudget.MIN_30, clickOn = false) }

            assertEquals(Settings(DailyBudget.MIN_30, clickOn = false), viewModel().state.value.settings)
        }

    @Test
    fun budgetControlStoresTheChoice() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.setDailyBudget(DailyBudget.MIN_15)
            runCurrent()

            assertEquals(DailyBudget.MIN_15, repository.settings.first().dailyBudget)
            assertEquals(DailyBudget.MIN_15, vm.state.value.settings.dailyBudget)
        }

    @Test
    fun weeklyGoalStepperStepsTheStoredDaysAndStaysInOneToSeven() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.changeWeeklyGoalDays(by = 1)
            runCurrent()
            assertEquals(5, vm.state.value.settings.weeklyGoalDays)

            vm.changeWeeklyGoalDays(by = 5)
            runCurrent()
            assertEquals(7, repository.settings.first().weeklyGoalDays)

            vm.changeWeeklyGoalDays(by = -10)
            runCurrent()
            assertEquals(1, repository.settings.first().weeklyGoalDays)
        }

    @Test
    fun twoQuickStepsBothCount() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.changeWeeklyGoalDays(by = 1)
            vm.changeWeeklyGoalDays(by = 1)
            runCurrent()

            assertEquals(6, repository.settings.first().weeklyGoalDays)
        }

    @Test
    fun clickSwitchStoresTheChoice() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.setClickOn(false)
            runCurrent()

            assertFalse(repository.settings.first().clickOn)
            assertFalse(vm.state.value.settings.clickOn)
        }

    @Test
    fun pedalSwitchStoresTheChoice() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertTrue(vm.state.value.settings.pedalControlOn)

            vm.setPedalControlOn(false)
            runCurrent()

            assertFalse(repository.settings.first().pedalControlOn)
            assertFalse(vm.state.value.settings.pedalControlOn)
        }

    @Test
    fun noteNamesChoiceIsSavedAndReadBack() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertEquals(NoteNames.AUTO, vm.state.value.settings.noteNames)

            vm.setNoteNames(NoteNames.OFF)
            runCurrent()

            assertEquals(NoteNames.OFF, repository.settings.first().noteNames)
            assertEquals(NoteNames.OFF, vm.state.value.settings.noteNames)
        }

    @Test
    fun strictnessIsSavedAndReadBack() =
        runTest(dispatcher) {
            val vm = viewModel()
            assertEquals(Strictness.STANDARD, vm.state.value.settings.strictness)

            vm.setStrictness(Strictness.GENTLE)
            runCurrent()

            assertEquals(Strictness.GENTLE, repository.settings.first().strictness)
            assertEquals(Strictness.GENTLE, vm.state.value.settings.strictness)
        }

    @Test
    fun updateCheckSwitchStoresTheChoice() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.setUpdateCheckOn(false)
            runCurrent()

            assertFalse(repository.settings.first().updateCheckOn)
            assertFalse(vm.state.value.settings.updateCheckOn)
        }

    @Test
    fun `storage failure shows the error card`() =
        runTest(dispatcher) {
            val vm = viewModel()
            repository.failing = true

            vm.setClickOn(false)
            runCurrent()

            assertTrue(vm.state.value.storageFailed)
            assertTrue(vm.state.value.settings.clickOn) // the control shows what is stored

            vm.dismissError()
            assertFalse(vm.state.value.storageFailed)
        }

    @Test
    fun anUnreadableStoreShowsTheErrorCardAndTheDefaults() =
        runTest(dispatcher) {
            repository.failing = true

            val vm = viewModel()

            assertEquals(SettingsState(Settings(), storageFailed = true), vm.state.value)
        }

    @Test
    fun aSuccessfulChangeClearsTheErrorCard() =
        runTest(dispatcher) {
            val vm = viewModel()
            repository.failing = true
            vm.setClickOn(false)
            runCurrent()
            repository.failing = false

            vm.setClickOn(false)
            runCurrent()

            assertFalse(vm.state.value.storageFailed)
        }

    @Test
    fun theScreenFollowsTheStoreAgainAfterAFailedFirstRead() =
        runTest(dispatcher) {
            repository.failing = true
            val vm = viewModel()
            repository.failing = false

            vm.setClickOn(false)
            runCurrent()

            assertFalse(vm.state.value.settings.clickOn)
            assertFalse(vm.state.value.storageFailed)
        }
}
