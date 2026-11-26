package pianoanchor.feature.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pianoanchor.engine.session.Card
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The sustain pedal as "start the first card" on Today. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayPedalTest : TodayViewModelFixture() {
    private fun TestScope.tapPedal() {
        piano.play(byteArrayOf(0xB0.toByte(), 64, 127))
        piano.play(byteArrayOf(0xB0.toByte(), 64, 0))
        runCurrent()
    }

    private fun TestScope.collectStarts(vm: TodayViewModel): MutableList<Card> {
        val starts = mutableListOf<Card>()
        backgroundScope.launch(dispatcher) { vm.pedalStarts.collect { starts += it } }
        runCurrent()
        return starts
    }

    @Test
    fun `a pedal tap starts the first card and the hint shows`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val starts = collectStarts(vm)
            assertTrue(vm.state.value.pedalHint)

            tapPedal()

            assertEquals(listOf(assertNotNull(vm.state.value.session).cards.first()), starts)
            vm.releasePiano()
        }

    @Test
    fun `the pedal does nothing with the setting off`() =
        runTest(dispatcher) {
            settings.update { it.copy(pedalControlOn = false) }
            val vm = viewModel()
            val starts = collectStarts(vm)

            tapPedal()

            assertTrue(starts.isEmpty())
            assertFalse(vm.state.value.pedalHint)
            vm.releasePiano()
        }

    @Test
    fun `an unreadable setting leaves the pedal off`() =
        runTest(dispatcher) {
            settings.failing = true
            val vm = viewModel()
            val starts = collectStarts(vm)

            tapPedal()

            assertTrue(starts.isEmpty())
            vm.releasePiano()
        }

    @Test
    fun `without a piano there is no pedal control and no hint`() =
        runTest(dispatcher) {
            piano.unplug()
            val vm = viewModel()

            assertFalse(vm.state.value.pedalHint)
            vm.releasePiano()
        }

    @Test
    fun `releasing the piano closes the reader and the pedal goes quiet`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val starts = collectStarts(vm)
            vm.releasePiano()

            tapPedal()

            assertTrue(starts.isEmpty())
            assertIs<TodayState>(vm.state.value)
        }

    @Test
    fun `a pedal tap before the plan has loaded starts nothing`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val starts = collectStarts(vm)
            learnerRepository.failing = true
            vm.refresh()
            runCurrent()

            tapPedal()

            assertTrue(starts.isEmpty())
            vm.releasePiano()
        }
}
