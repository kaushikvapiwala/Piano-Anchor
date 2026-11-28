package pianoanchor.feature.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import pianoanchor.engine.session.SessionRunner
import pianoanchor.engine.session.SessionStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Today's session hero (steps, minutes), the day's progress and the week's pause. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodaySessionTest : TodayViewModelFixture() {
    private val monday = LocalDate(2026, 11, 16)

    @Test
    fun `the state lists the session's steps in run order with their minutes`() =
        runTest(dispatcher) {
            val vm = viewModel()

            val state = vm.state.value
            val session = assertNotNull(state.session)
            assertEquals(SessionRunner.steps(session), state.steps)
            assertTrue(state.steps.isNotEmpty())
            assertEquals(session.cards.sumOf { it.minutes }, state.steps.sumOf { it.minutes })
            assertEquals(session.cards.indexOf(state.steps.first().card), state.cardIndex(state.steps.first()))
            assertFalse(state.doneToday)
        }

    @Test
    fun `finished steps leave the list and the recap marks the day done`() =
        runTest(dispatcher) {
            val first =
                assertNotNull(
                    viewModel()
                        .state.value.steps
                        .first(),
                )
            progress.markFinished(today, first)
            progress.markCompleted(today)

            val state = viewModel().state.value

            assertFalse(first in state.steps)
            assertTrue(state.doneToday)
            assertFalse(state.pedalHint)
        }

    @Test
    fun `without a session a card index is minus one`() = assertEquals(-1, TodayState().cardIndex(dummyStep()))

    private fun dummyStep(): SessionStep =
        SessionStep.Ear(
            pianoanchor.engine.session.Card
                .Ear(emptyList(), 1),
            1,
        )

    @Test
    fun `the week status counts days, goal and a fresh pause`() =
        runTest(dispatcher) {
            playedOn("2026-11-16")
            val week = assertNotNull(viewModel().state.value.week)

            assertEquals(monday, week.weekStart)
            assertEquals(1, week.daysPlayed)
            assertEquals(4, week.goalDays)
            assertTrue(week.canPause)
        }

    @Test
    fun `pausing the week writes the Monday to the settings and the card turns paused`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.pauseWeek()
            runCurrent()

            assertEquals(setOf(monday), settings.settings.first().pausedWeeks)
            val week = assertNotNull(vm.state.value.week)
            assertTrue(week.paused)
            assertFalse(week.canPause)
            assertEquals(1, week.streakWeeks)
        }

    @Test
    fun `pausing twice or with no pause left changes nothing`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.pauseWeek()
            vm.pauseWeek()
            runCurrent()
            vm.pauseWeek()
            runCurrent()

            assertEquals(setOf(monday), settings.settings.first().pausedWeeks)
        }

    @Test
    fun `a pause already stored by another screen is not stored twice`() =
        runTest(dispatcher) {
            val vm = viewModel()
            settings.update { it.copy(pausedWeeks = setOf(monday)) }

            vm.pauseWeek()
            runCurrent()

            assertEquals(setOf(monday), settings.settings.first().pausedWeeks)
        }

    @Test
    fun `a settings store that fails leaves the week unpaused`() =
        runTest(dispatcher) {
            val vm = viewModel()
            settings.failing = true

            vm.pauseWeek()
            runCurrent()
            settings.failing = false

            assertEquals(emptySet(), settings.settings.first().pausedWeeks)
            assertIs<pianoanchor.engine.session.WeekStatus>(vm.state.value.week)
        }
}
