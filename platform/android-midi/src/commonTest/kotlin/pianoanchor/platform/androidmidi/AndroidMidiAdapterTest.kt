package pianoanchor.platform.androidmidi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.NoteOn
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidMidiAdapterTest {
    private val system = FakeMidiSystem(PIANO)
    private val adapter = AndroidMidiAdapter(system)
    private val piano = MidiDevice(id = "usb:Acme/Stage Piano/SN1", name = "Acme Stage Piano")

    private fun TestScope.collect(device: MidiDevice = piano): MutableList<MidiEvent> {
        val events = mutableListOf<MidiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            adapter.events(device).collect { events.add(it) }
        }
        return events
    }

    @Test
    fun timestampsAreClampedWhenThePlatformGoesBackwards() =
        runTest {
            val events = collect()
            system.deliver(PIANO.systemId, NOTE_ON_C4, timestampNanos = 500)
            system.deliver(PIANO.systemId, NOTE_ON_C4, timestampNanos = 400)
            system.deliver(PIANO.systemId, NOTE_ON_C4, timestampNanos = 600)
            assertEquals(listOf(500L, 500L, 600L), events.map { it.timestampNanos })
        }

    @Test
    fun onePlatformCallWithTwoMessagesGivesTwoEventsWithTheSameStamp() =
        runTest {
            val events = collect()
            system.deliver(PIANO.systemId, NOTE_ON_C4 + SUSTAIN_DOWN, timestampNanos = 900)
            assertIs<NoteOn>(events[0])
            assertIs<ControlChange>(events[1])
            assertEquals(listOf(900L, 900L), events.map { it.timestampNanos })
        }

    @Test
    fun runningStatusAcrossCallsIsParsed() =
        runTest {
            val events = collect()
            system.deliver(PIANO.systemId, NOTE_ON_C4)
            system.deliver(PIANO.systemId, byteArrayOf(0x00, 62, 90), offset = 1)
            assertEquals(listOf(60, 62), events.map { assertIs<NoteOn>(it).note.number })
        }

    @Test
    fun secondCollectorOnSameDeviceFails() =
        runTest {
            collect()
            assertFailsWith<IllegalStateException> { adapter.events(piano).first() }
        }

    @Test
    fun cancellingCollectionClosesTheDeviceAndAllowsReopening() =
        runTest {
            val job =
                backgroundScope.launch(
                    UnconfinedTestDispatcher(testScheduler),
                ) { adapter.events(piano).collect {} }
            job.cancel()
            assertEquals(1, system.inputsClosed.get())
            val events = collect()
            system.deliver(PIANO.systemId, NOTE_ON_C4)
            assertEquals(1, events.size)
            assertEquals(2, system.inputsOpened.get())
        }

    @Test
    fun eventsFailWhenTheDeviceCannotBeOpened() =
        runTest {
            system.opensSucceed = false
            assertFailsWith<MidiDeviceUnavailableException> { adapter.events(piano).first() }
            system.opensSucceed = true
            collect() // the failed attempt left the device free to open
            assertEquals(1, system.inputsOpened.get())
        }

    @Test
    fun deviceWithoutOutputPortsIsNotAnInputDevice() =
        runTest {
            val receiveOnly = PIANO.copy(systemId = 2, serial = "SN2", outputPorts = 0)
            system.plugIn(receiveOnly)
            val id = "usb:Acme/Stage Piano/SN2"
            assertEquals(listOf(piano), adapter.inputDevices.first())
            assertTrue(adapter.outputDevices.first().any { it.id == id })
            assertFailsWith<MidiDeviceUnavailableException> { adapter.events(MidiDevice(id, "x")).first() }
        }

    @Test
    fun deviceWithoutInputPortsIsNotAnOutputDevice() =
        runTest {
            val sendOnly = PIANO.copy(systemId = 2, serial = "SN2", inputPorts = 0)
            system.plugIn(sendOnly)
            val id = "usb:Acme/Stage Piano/SN2"
            assertEquals(listOf(piano), adapter.outputDevices.first())
            assertFailsWith<MidiDeviceUnavailableException> { adapter.send(MidiDevice(id, "x"), NOTE_ON_C4) }
        }

    @Test
    fun sendOpensInputPortOnceAndReusesIt() =
        runTest {
            adapter.send(piano, NOTE_ON_C4)
            adapter.send(piano, SUSTAIN_DOWN)
            assertEquals(1, system.outputsOpened.get())
            assertContentEquals(SUSTAIN_DOWN, system.sent[1])
        }

    @Test
    fun senderIsClosedOnceItsDeviceHasLeftTheList() =
        runTest {
            adapter.send(piano, NOTE_ON_C4)
            system.unplug(PIANO.systemId)
            assertFailsWith<MidiDeviceUnavailableException> { adapter.send(piano, NOTE_ON_C4) }
            assertEquals(1, system.outputsClosed.get())
            system.plugIn(PIANO.copy(systemId = 5))
            adapter.send(piano, NOTE_ON_C4)
            assertEquals(2, system.outputsOpened.get())
        }

    @Test
    fun sendFailsWhenTheDeviceCannotBeOpened() =
        runTest {
            system.opensSucceed = false
            assertFailsWith<MidiDeviceUnavailableException> { adapter.send(piano, NOTE_ON_C4) }
            assertTrue(system.sent.isEmpty())
        }

    @Test
    fun aClosedPlatformPortIsReportedAsUnavailableAndReopenedOnRetry() =
        runTest {
            adapter.send(piano, NOTE_ON_C4)
            system.sendsFail = true
            val failure = assertFailsWith<MidiDeviceUnavailableException> { adapter.send(piano, NOTE_ON_C4) }
            assertIs<IOException>(failure.cause)
            assertEquals(1, system.outputsClosed.get())
            system.sendsFail = false
            adapter.send(piano, SUSTAIN_DOWN)
            assertEquals(2, system.outputsOpened.get())
        }

    @Test
    fun aBadPlatformRangeFailsTheFlowInsteadOfThePlatformThread() =
        runTest {
            var failure: Throwable? = null
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                failure = runCatching { adapter.events(piano).collect {} }.exceptionOrNull()
            }
            system.deliver(PIANO.systemId, NOTE_ON_C4, offset = 0, count = NOTE_ON_C4.size + 1)
            assertIs<IllegalArgumentException>(failure)
            assertEquals(1, system.inputsClosed.get())
        }

    private companion object {
        val NOTE_ON_C4 = byteArrayOf(0x90.toByte(), 60, 100)
        val SUSTAIN_DOWN = byteArrayOf(0xB0.toByte(), 64, 127)
    }
}
