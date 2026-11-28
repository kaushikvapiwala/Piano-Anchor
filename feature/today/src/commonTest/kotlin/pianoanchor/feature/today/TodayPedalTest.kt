package pianoanchor.feature.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
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

    private fun TestScope.collectStarts(vm: TodayViewModel): MutableList<Unit> {
        val starts = mutableListOf<Unit>()
        backgroundScope.launch(dispatcher) { vm.pedalStarts.collect { starts += it } }
        runCurrent()
        return starts
    }

    @Test
    fun `a pedal tap starts the session and the hint shows`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val starts = collectStarts(vm)
            assertTrue(vm.state.value.pedalHint)

            tapPedal()

            assertEquals(listOf(Unit), starts)
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

    @Test
    fun `the piano is not opened while the pedal control is off and opens when it is turned on`() =
        runTest(dispatcher) {
            settings.update { it.copy(pedalControlOn = false) }
            var open = 0
            val counting =
                object : MidiInputPort by piano {
                    override fun events(device: MidiDevice) =
                        piano.events(device).onStart { open++ }.onCompletion { open-- }
                }
            val vm =
                TodayViewModel(
                    LearnerModel(Curriculum.graph, learnerRepository),
                    settings,
                    attempts,
                    counting,
                    piano,
                    calibration,
                    clock,
                    TimeZone.UTC,
                    updateCheck,
                    INSTALLED,
                    SessionProgress(settings),
                )
            runCurrent()
            assertEquals(0, open)

            settings.update { it.copy(pedalControlOn = true) }
            runCurrent()
            assertEquals(1, open)

            settings.update { it.copy(pedalControlOn = false) }
            runCurrent()
            assertEquals(0, open)
            vm.releasePiano()
        }

    @Test
    fun `one pedal request is sent and none once a navigation has begun`() =
        runTest(dispatcher) {
            val vm = viewModel()
            val starts = collectStarts(vm)

            tapPedal()
            tapPedal()
            assertEquals(1, starts.size)
            vm.releasePiano()

            val other = viewModel()
            val none = collectStarts(other)
            assertTrue(other.beginLeaving())
            assertFalse(other.beginLeaving())
            tapPedal()
            assertTrue(none.isEmpty())
            other.releasePiano()
        }
}
