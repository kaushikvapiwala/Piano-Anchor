package pianoanchor.platform.androidmidi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.NoteOn
import pianoanchor.engine.Tunables
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidMidiAdapterTest {
    private val system = FakeMidiSystem(PIANO)
    private val adapter = AndroidMidiAdapter(system, CoroutineScope(Job()))
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

    /** An adapter whose reconnecting runs in this test's virtual time. */
    private fun TestScope.bleAdapter() = AndroidMidiAdapter(system, backgroundScope)

    private suspend fun AndroidMidiAdapter.isListed(device: MidiDevice = BLE_PIANO) = device in inputDevices.first()

    /** Advertises, scans and connects [BLE_PIANO]. */
    private suspend fun TestScope.connectedBleAdapter(): AndroidMidiAdapter {
        val ble = bleAdapter()
        system.advertise(BluetoothAdvertisement(BLE_ADDRESS, BLE_PIANO.name))
        ble.scan().first { BLE_PIANO in it }
        ble.connect(BLE_PIANO)
        return ble
    }

    @Test
    fun scanUsesAPlaceholderNameAndTheUpperCasedAddress() =
        runTest {
            system.advertise(BluetoothAdvertisement("aa:bb:cc:00:00:01", null))
            system.advertise(BluetoothAdvertisement("aa:bb:cc:00:00:02", " "))
            val found = bleAdapter().scan().toList().last()
            assertEquals(
                listOf(
                    MidiDevice("ble:AA:BB:CC:00:00:01", FALLBACK_DEVICE_NAME),
                    MidiDevice("ble:AA:BB:CC:00:00:02", FALLBACK_DEVICE_NAME),
                ),
                found,
            )
        }

    @Test
    fun scanFailsWhenThePlatformRefusesIt() =
        runTest {
            system.scansFail = true
            assertFailsWith<BluetoothUnavailableException> { bleAdapter().scan().toList() }
        }

    @Test
    fun connectFailsWhenTheDeviceCannotBeOpened() =
        runTest {
            val ble = bleAdapter()
            system.advertise(BluetoothAdvertisement(BLE_ADDRESS, BLE_PIANO.name))
            ble.scan().first { BLE_PIANO in it }
            system.opensSucceed = false
            assertFailsWith<MidiDeviceUnavailableException> { ble.connect(BLE_PIANO) }
            assertFalse(ble.isListed())
        }

    @Test
    fun connectFailsAndClosesTheDeviceWhenItIsNotListedInTime() =
        runTest {
            val ble = bleAdapter()
            system.advertise(BluetoothAdvertisement(BLE_ADDRESS, BLE_PIANO.name))
            ble.scan().first { BLE_PIANO in it }
            system.listsOpenedBluetooth = false
            val start = currentTime
            assertFailsWith<MidiDeviceUnavailableException> { ble.connect(BLE_PIANO) }
            assertEquals(Tunables.BLE_LISTED_TIMEOUT_MS, currentTime - start)
            assertEquals(1, system.bluetoothClosed.get())
        }

    @Test
    fun cancellingConnectWhileWaitingForTheListingClosesTheDevice() =
        runTest {
            val ble = bleAdapter()
            system.advertise(BluetoothAdvertisement(BLE_ADDRESS, BLE_PIANO.name))
            ble.scan().first { BLE_PIANO in it }
            system.listsOpenedBluetooth = false
            val connecting = launch { ble.connect(BLE_PIANO) }
            advanceTimeBy(1_000)
            connecting.cancel()
            connecting.join()
            assertEquals(1, system.bluetoothClosed.get())
        }

    @Test
    fun aReopenThatIsNotListedInTimeIsClosedAndRetried() =
        runTest {
            val ble = connectedBleAdapter()
            system.listsOpenedBluetooth = false
            system.dropBluetooth(BLE_ADDRESS)
            val dropped = currentTime
            advanceTimeBy(1_000 + Tunables.BLE_LISTED_TIMEOUT_MS + 1)
            assertEquals(2, system.bluetoothClosed.get()) // the dead handle and the unlisted reopen
            system.listsOpenedBluetooth = true
            ble.inputDevices.first { BLE_PIANO in it }
            assertEquals(1_000L + Tunables.BLE_LISTED_TIMEOUT_MS + 2_000, currentTime - dropped)
        }

    @Test
    fun aDropClosesTheDeadHandleAndDisconnectClosesTheLiveOne() =
        runTest {
            val ble = connectedBleAdapter()
            system.dropBluetooth(BLE_ADDRESS)
            ble.inputDevices.first { BLE_PIANO in it }
            assertEquals(1, system.bluetoothClosed.get())
            ble.disconnect(BLE_PIANO)
            assertEquals(2, system.bluetoothClosed.get())
            assertEquals(2, system.bluetoothOpened.get())
        }

    @Test
    fun disconnectDuringTheBackoffClosesNothingTwice() =
        runTest {
            val ble = connectedBleAdapter()
            system.dropBluetooth(BLE_ADDRESS)
            ble.disconnect(BLE_PIANO)
            assertEquals(1, system.bluetoothClosed.get())
        }

    @Test
    fun failedReopensBackOffUntilOneSucceeds() =
        runTest {
            val ble = connectedBleAdapter()
            system.opensSucceed = false
            system.dropBluetooth(BLE_ADDRESS)
            val dropped = currentTime
            advanceTimeBy(1_000 + 2_000 + 1)
            assertFalse(ble.isListed())
            system.opensSucceed = true
            ble.inputDevices.first { BLE_PIANO in it }
            assertEquals(1_000L + 2_000 + 4_000, currentTime - dropped)
        }

    @Test
    fun noReopenIsTriedWhileBluetoothIsOff() =
        runTest {
            val ble = connectedBleAdapter()
            system.bluetoothState = BluetoothUnavailableException.Reason.BLUETOOTH_OFF
            system.dropBluetooth(BLE_ADDRESS)
            val dropped = currentTime
            advanceTimeBy(1_000 + 1)
            assertEquals(1, system.bluetoothOpened.get())
            system.bluetoothState = null
            ble.inputDevices.first { BLE_PIANO in it }
            assertEquals(1_000L + 2_000, currentTime - dropped)
        }

    @Test
    fun theBackoffStartsOverAfterASuccessfulReopen() =
        runTest {
            val ble = connectedBleAdapter()
            repeat(2) {
                system.dropBluetooth(BLE_ADDRESS)
                val dropped = currentTime
                ble.inputDevices.first { BLE_PIANO in it }
                assertEquals(Tunables.BLE_RECONNECT_INITIAL_MS, currentTime - dropped)
            }
        }

    @Test
    fun reconnectingGivesUpAndForgetsTheDevice() =
        runTest {
            val ble = connectedBleAdapter()
            system.opensSucceed = false
            system.dropBluetooth(BLE_ADDRESS)
            advanceTimeBy(Tunables.BLE_RECONNECT_GIVE_UP_MS + 1)
            system.opensSucceed = true
            advanceTimeBy(Tunables.BLE_RECONNECT_GIVE_UP_MS)
            assertFalse(ble.isListed())
            assertEquals(1, system.bluetoothOpened.get())
            ble.connect(BLE_PIANO) // forgotten, so this opens it afresh
            assertTrue(ble.isListed())
        }

    @Test
    fun connectingADroppedDeviceReopensItAtOnceAndStopsTheOldRetries() =
        runTest {
            val ble = connectedBleAdapter()
            system.dropBluetooth(BLE_ADDRESS)
            ble.connect(BLE_PIANO)
            assertTrue(ble.isListed())
            advanceTimeBy(Tunables.BLE_RECONNECT_GIVE_UP_MS)
            assertEquals(2, system.bluetoothOpened.get())
        }

    @Test
    fun eventsOfABluetoothDeviceCompleteOnADropAndWorkAgainAfterTheReopen() =
        runTest {
            val ble = connectedBleAdapter()
            var completed = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ble.events(BLE_PIANO).toList()
                completed = true
            }
            system.dropBluetooth(BLE_ADDRESS)
            assertTrue(completed)
            ble.inputDevices.first { BLE_PIANO in it }
            val events = mutableListOf<MidiEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { ble.events(BLE_PIANO).toList(events) }
            val reopened = system.devices.value.single { it.bluetoothAddress == BLE_ADDRESS }
            system.deliver(reopened.systemId, NOTE_ON_C4)
            assertEquals(1, events.size)
        }

    private companion object {
        const val BLE_ADDRESS = "AA:BB:CC:DD:EE:01"
        val BLE_PIANO = MidiDevice("ble:$BLE_ADDRESS", "Acme MIDI")
        val NOTE_ON_C4 = byteArrayOf(0x90.toByte(), 60, 100)
        val SUSTAIN_DOWN = byteArrayOf(0xB0.toByte(), 64, 127)
    }
}
