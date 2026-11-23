package pianoanchor.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.LinkResumed
import pianoanchor.core.model.NoteOn
import pianoanchor.core.ports.MidiInputPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * What an input adapter must also do for a device whose link can drop and come back (Bluetooth): ride out a brief
 * drop, and give up when the device stays gone. Subclass it for such adapters; the others run
 * [MidiInputPortContractTest] only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MidiInputRideOutContractTest : MidiInputPortContractTest() {
    /** An [InputHarness] that can also drop the device's link. */
    protected interface RideOutHarness : InputHarness {
        /**
         * The link drops and is reopened within the grace period. [duringGap] runs after the drop and before the
         * reopen; returns after the adapter reopened the device, so [deliver] reaches collectors again.
         */
        suspend fun dropLink(duringGap: suspend () -> Unit)

        /** The link drops and the device stays gone; returns after the grace period passed. */
        suspend fun dropForGood()
    }

    protected abstract fun createRideOutHarness(scope: TestScope): RideOutHarness

    final override fun createHarness(scope: TestScope): InputHarness = createRideOutHarness(scope)

    @Test
    fun aDroppedAndReopenedDeviceStaysListedAndItsEventsCarryOn() =
        runTest {
            val h = createRideOutHarness(this)
            val events = collect(h)
            val listedInTheGap = mutableListOf<Boolean>()
            h.deliver(byteArrayOf(NOTE_ON, C4, VELOCITY))
            h.dropLink { listedInTheGap += h.device in h.port.inputDevices.first() }
            assertTrue(h.device in h.port.inputDevices.first())
            h.deliver(byteArrayOf(CONTROL_CHANGE, SUSTAIN, PEDAL_DOWN))
            assertEquals(listOf(true), listedInTheGap)
            assertIs<NoteOn>(events[0])
            assertIs<LinkResumed>(events[1])
            val stamps = events.map { it.timestampNanos }
            assertEquals(stamps.sorted(), stamps) // LinkResumed is stamped no earlier than the message before it
            assertIs<ControlChange>(events[2])
            assertEquals(EVENT_COUNT, events.size)
        }

    @Test
    fun aDeviceThatStaysGoneCompletesTheFlowAndLeavesTheList() =
        runTest {
            val h = createRideOutHarness(this)
            var completed = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                h.port.events(h.device).toList()
                completed = true
            }
            assertFalse(completed)
            h.dropForGood()
            assertTrue(completed)
            assertFalse(h.device in h.port.inputDevices.first())
        }
}
