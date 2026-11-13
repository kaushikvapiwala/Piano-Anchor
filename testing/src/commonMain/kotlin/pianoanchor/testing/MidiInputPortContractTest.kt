package pianoanchor.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.ports.MidiInputPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Behaviour every [MidiInputPort] adapter must satisfy. Subclass it once per adapter. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MidiInputPortContractTest {
    /** What a contract test needs to drive an input adapter from the instrument side. */
    protected interface InputHarness {
        val port: MidiInputPort
        val device: MidiDevice // listed when the harness is created

        /** The instrument sends these bytes now; returns after the adapter has handed the events to collectors. */
        suspend fun deliver(bytes: ByteArray)

        /** Disconnects the device; returns after it left the device list and open `events` flows completed. */
        suspend fun unplug()
    }

    protected abstract fun createHarness(): InputHarness

    @Test
    fun listsTheConnectedDevice() =
        runTest {
            val h = createHarness()
            assertTrue(h.device in h.port.inputDevices.first())
        }

    @Test
    fun unplugRemovesTheDevice() =
        runTest {
            val h = createHarness()
            h.unplug()
            assertFalse(h.device in h.port.inputDevices.first())
        }

    @Test
    fun deliversEventsInArrivalOrder() =
        runTest {
            val h = createHarness()
            val events = collect(h)
            h.deliver(byteArrayOf(NOTE_ON, C4, VELOCITY))
            h.deliver(byteArrayOf(NOTE_OFF, C4, RELEASED))
            h.deliver(byteArrayOf(CONTROL_CHANGE, SUSTAIN, PEDAL_DOWN))
            val on = assertIs<NoteOn>(events[0])
            assertEquals(MidiNote(C4.toInt()), on.note)
            assertEquals(VELOCITY.toInt(), on.velocity)
            assertEquals(MidiNote(C4.toInt()), assertIs<NoteOff>(events[1]).note)
            val cc = assertIs<ControlChange>(events[2])
            assertEquals(SUSTAIN.toInt(), cc.controller)
            assertEquals(PEDAL_DOWN.toInt(), cc.value)
            assertEquals(EVENT_COUNT, events.size)
        }

    @Test
    fun timestampsNeverGoBackwards() =
        runTest {
            val h = createHarness()
            val events = collect(h)
            repeat(EVENT_COUNT) { h.deliver(byteArrayOf(NOTE_ON, C4, VELOCITY)) }
            val stamps = events.map { it.timestampNanos }
            assertEquals(EVENT_COUNT, stamps.size)
            assertEquals(stamps.sorted(), stamps)
        }

    @Test
    fun eventsCompleteWhenDeviceUnplugged() =
        runTest {
            val h = createHarness()
            var completed = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                h.port.events(h.device).toList()
                completed = true
            }
            assertFalse(completed)
            h.unplug()
            assertTrue(completed)
        }

    @Test
    fun messagesWithoutACollectorAreDropped() =
        runTest {
            val h = createHarness()
            h.deliver(byteArrayOf(NOTE_ON, C4, VELOCITY))
            val events = collect(h)
            h.deliver(byteArrayOf(CONTROL_CHANGE, SUSTAIN, PEDAL_DOWN))
            assertEquals(1, events.size)
            assertIs<ControlChange>(events[0])
        }

    @Test
    fun openingAnAbsentDeviceFails() =
        runTest {
            val h = createHarness()
            assertFailsWith<MidiDeviceUnavailableException> {
                h.port.events(MidiDevice("missing", "x")).first()
            }
        }

    private fun kotlinx.coroutines.test.TestScope.collect(h: InputHarness): MutableList<MidiEvent> {
        val events = mutableListOf<MidiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            h.port.events(h.device).collect { events.add(it) }
        }
        return events
    }

    private companion object {
        const val NOTE_ON: Byte = 0x90.toByte()
        const val NOTE_OFF: Byte = 0x80.toByte()
        const val CONTROL_CHANGE: Byte = 0xB0.toByte()
        const val C4: Byte = 60
        const val SUSTAIN: Byte = 64
        const val VELOCITY: Byte = 100
        const val RELEASED: Byte = 0
        const val PEDAL_DOWN: Byte = 127
        const val EVENT_COUNT = 3
    }
}
