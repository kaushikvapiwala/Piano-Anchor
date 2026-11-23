package pianoanchor.platform.androidmidi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.LinkResumed
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
    fun connectOpensAKnownBluetoothAddressWithoutAScan() =
        runTest {
            val ble = bleAdapter()
            system.advertise(BluetoothAdvertisement(BLE_ADDRESS, BLE_PIANO.name))
            ble.connect(BLE_PIANO)
            assertTrue(ble.isListed())
        }

    @Test
    fun connectWithoutAScanKeepsTheListingWaitAndFailsWhenTheDeviceIsNotThere() =
        runTest {
            val ble = bleAdapter()
            assertFailsWith<MidiDeviceUnavailableException> { ble.connect(BLE_PIANO) }
            system.advertise(BluetoothAdvertisement(BLE_ADDRESS, BLE_PIANO.name))
            system.listsOpenedBluetooth = false
            val start = currentTime
            assertFailsWith<MidiDeviceUnavailableException> { ble.connect(BLE_PIANO) }
            assertEquals(Tunables.BLE_LISTED_TIMEOUT_MS, currentTime - start)
        }

    @Test
    fun connectStillNeedsAScanForADeviceThatIsNotBluetooth() =
        runTest {
            val ble = bleAdapter()
            val usb = MidiDevice("usb:Roland/FP-30X/123", "FP-30X")
            assertFailsWith<MidiDeviceUnavailableException> { ble.connect(usb) }
            assertFailsWith<MidiDeviceUnavailableException> { ble.connect(MidiDevice("sys:7", "Other")) }
        }

    @Test
    fun cancellingAConnectThatNeverOpensLeavesTheAdapterFreeForTheNextOne() =
        runTest {
            val ble = bleAdapter()
            system.advertise(BluetoothAdvertisement(BLE_ADDRESS, BLE_PIANO.name))
            system.bluetoothOpenGate = CompletableDeferred()
            val stuck = launch { ble.connect(BLE_PIANO) }
            runCurrent()
            stuck.cancelAndJoin()

            system.bluetoothOpenGate = null
            ble.connect(BLE_PIANO)
            ble.disconnect(BLE_PIANO)

            assertTrue(stuck.isCancelled)
            assertEquals(1, system.bluetoothOpened.get())
            assertEquals(1, system.bluetoothClosed.get())
            assertFalse(ble.isListed())
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
            system.devices.first { list -> list.any { it.bluetoothAddress == BLE_ADDRESS } }
            assertEquals(1_000L + Tunables.BLE_LISTED_TIMEOUT_MS + 2_000, currentTime - dropped)
        }

    @Test
    fun aDropClosesTheDeadHandleAndDisconnectClosesTheLiveOne() =
        runTest {
            val ble = connectedBleAdapter()
            system.dropBluetooth(BLE_ADDRESS)
            system.devices.first { list -> list.any { it.bluetoothAddress == BLE_ADDRESS } }
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
            assertTrue(system.devices.value.none { it.bluetoothAddress == BLE_ADDRESS }) // still held in the list
            system.opensSucceed = true
            system.devices.first { list -> list.any { it.bluetoothAddress == BLE_ADDRESS } }
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
            system.devices.first { list -> list.any { it.bluetoothAddress == BLE_ADDRESS } }
            assertEquals(1_000L + 2_000, currentTime - dropped)
        }

    @Test
    fun theBackoffStartsOverAfterASuccessfulReopen() =
        runTest {
            val ble = connectedBleAdapter()
            repeat(2) {
                system.dropBluetooth(BLE_ADDRESS)
                val dropped = currentTime
                system.devices.first { list -> list.any { it.bluetoothAddress == BLE_ADDRESS } }
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
    fun aDropReopenedWithinTheGraceChangesNoListingAndEmitsLinkResumed() =
        runTest {
            val ble = connectedBleAdapter()
            val listings = mutableListOf<List<MidiDevice>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ble.inputDevices.collect { listings += it.filter { device -> device == BLE_PIANO } }
            }
            val events = mutableListOf<MidiEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { ble.events(BLE_PIANO).toList(events) }
            val first = system.devices.value.single { it.bluetoothAddress == BLE_ADDRESS }
            system.deliver(first.systemId, NOTE_ON_C4, timestampNanos = Long.MAX_VALUE / 2)

            system.dropBluetooth(BLE_ADDRESS)
            system.devices.first { list -> list.any { it.bluetoothAddress == BLE_ADDRESS } }
            val reopened = system.devices.value.single { it.bluetoothAddress == BLE_ADDRESS }
            system.deliver(reopened.systemId, SUSTAIN_DOWN)

            assertEquals(listOf(listOf(BLE_PIANO)), listings)
            assertIs<NoteOn>(events[0])
            val resumed = assertIs<LinkResumed>(events[1])
            assertTrue(resumed.timestampNanos >= events[0].timestampNanos)
            assertIs<ControlChange>(events[2])
            assertEquals(3, events.size)
            assertEquals(1, system.inputsClosed.get()) // the dead input, closed once
            assertEquals(2, system.inputsOpened.get())
        }

    @Test
    fun theNewLinkIsParsedAfreshWithoutTheDeadLinksRunningStatus() =
        runTest {
            val ble = connectedBleAdapter()
            val events = mutableListOf<MidiEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { ble.events(BLE_PIANO).toList(events) }
            val first = system.devices.value.single { it.bluetoothAddress == BLE_ADDRESS }
            system.deliver(first.systemId, NOTE_ON_C4) // leaves "note on" as the running status

            system.dropBluetooth(BLE_ADDRESS)
            system.devices.first { list -> list.any { it.bluetoothAddress == BLE_ADDRESS } }
            val reopened = system.devices.value.single { it.bluetoothAddress == BLE_ADDRESS }
            system.deliver(reopened.systemId, byteArrayOf(62, 90)) // data bytes with no status: nothing to decode
            system.deliver(reopened.systemId, byteArrayOf(0x90.toByte(), 64, 90))

            assertIs<NoteOn>(events[0])
            assertIs<LinkResumed>(events[1])
            assertEquals(64, assertIs<NoteOn>(events[2]).note.number)
            assertEquals(3, events.size)
        }

    @Test
    fun aPianoBackBeforeTheListsGraceEndsKeepsTheFlowOpenEvenWhenItsInputClosedEarlier() =
        runTest {
            val ble = connectedBleAdapter()
            val listings = mutableListOf<List<MidiDevice>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ble.inputDevices.collect { listings += it.filter { device -> device == BLE_PIANO } }
            }
            val events = mutableListOf<MidiEvent>()
            var completed = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ble.events(BLE_PIANO).toList(events)
                completed = true
            }
            system.bluetoothState = BluetoothUnavailableException.Reason.BLUETOOTH_OFF // no reopen by the app
            system.closeBluetoothInput(BLE_ADDRESS) // the input closes first ...
            advanceTimeBy(3_000)
            system.unlistBluetooth(BLE_ADDRESS) // ... the system list drops the device 3 s later
            advanceTimeBy(Tunables.BLE_DROP_GRACE_MS - 1_000) // past 5 s after the close, inside the list's grace
            assertFalse(completed)

            system.plugIn(
                SystemDevice(200, Transport.BLUETOOTH, 1, 1, bluetoothAddress = BLE_ADDRESS, name = "Acme MIDI"),
            )
            runCurrent()

            assertFalse(completed)
            assertEquals(listOf(listOf(BLE_PIANO)), listings)
            assertIs<LinkResumed>(events.single())
        }

    @Test
    fun anInputThatClosesWhileTheDeviceStaysListedCompletesTheFlowAfterTheGrace() =
        runTest {
            val ble = connectedBleAdapter()
            var completed = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ble.events(BLE_PIANO).toList()
                completed = true
            }
            system.closeBluetoothInput(BLE_ADDRESS) // the device itself is still listed, same handle
            advanceTimeBy(Tunables.BLE_DROP_GRACE_MS - 1)
            assertFalse(completed)
            advanceTimeBy(2)
            assertTrue(completed)
        }

    @Test
    fun disconnectWhileTheDeadDeviceIsStillListedEndsTheFlowAtOnce() =
        runTest {
            val ble = connectedBleAdapter()
            var completed = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ble.events(BLE_PIANO).toList()
                completed = true
            }
            system.closingUnlists = false
            system.closeBluetoothInput(BLE_ADDRESS)
            assertFalse(completed)
            val start = currentTime

            ble.disconnect(BLE_PIANO) // the system still lists the device afterwards

            assertTrue(completed)
            assertEquals(0, currentTime - start)
        }

    @Test
    fun disconnectWhileWaitingForTheReopenEndsTheFlowAtOnce() =
        runTest {
            val ble = connectedBleAdapter()
            var completed = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ble.events(BLE_PIANO).toList()
                completed = true
            }
            system.opensSucceed = false
            system.dropBluetooth(BLE_ADDRESS)
            assertFalse(completed)
            val start = currentTime

            ble.disconnect(BLE_PIANO)

            assertTrue(completed)
            assertEquals(0, currentTime - start)
        }

    @Test
    fun aDropThatLastsBeyondTheGraceRemovesTheDeviceAndCompletesTheFlow() =
        runTest {
            val ble = connectedBleAdapter()
            val listings = mutableListOf<List<MidiDevice>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ble.inputDevices.collect { listings += it.filter { device -> device == BLE_PIANO } }
            }
            var completed = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ble.events(BLE_PIANO).toList()
                completed = true
            }
            system.opensSucceed = false
            system.dropBluetooth(BLE_ADDRESS)

            advanceTimeBy(Tunables.BLE_DROP_GRACE_MS - 1)
            assertEquals(listOf(listOf(BLE_PIANO)), listings)
            assertFalse(completed)
            advanceTimeBy(2)
            assertEquals(listOf(listOf(BLE_PIANO), emptyList()), listings)
            assertTrue(completed)
        }

    @Test
    fun disconnectRemovesTheDeviceAtOnce() =
        runTest {
            val ble = connectedBleAdapter()
            ble.disconnect(BLE_PIANO)
            assertFalse(ble.isListed())
        }

    @Test
    fun disconnectWhileTheDeviceIsHeldRemovesItAtOnce() =
        runTest {
            val ble = connectedBleAdapter()
            val listings = mutableListOf<List<MidiDevice>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ble.inputDevices.collect { listings += it.filter { device -> device == BLE_PIANO } }
            }
            system.opensSucceed = false
            system.dropBluetooth(BLE_ADDRESS)
            assertEquals(listOf(listOf(BLE_PIANO)), listings) // held

            ble.disconnect(BLE_PIANO)

            assertEquals(listOf(listOf(BLE_PIANO), emptyList()), listings)
            assertEquals(0, currentTime)
        }

    @Test
    fun aDeviceTheReconnectingGaveUpOnIsRemovedAtOnce() =
        runTest {
            val ble = connectedBleAdapter()
            system.opensSucceed = false
            system.dropBluetooth(BLE_ADDRESS)
            val dropped = currentTime
            advanceTimeBy(Tunables.BLE_RECONNECT_GIVE_UP_MS + 1)
            assertFalse(ble.isListed())
            assertTrue(currentTime - dropped > Tunables.BLE_DROP_GRACE_MS)
        }

    @Test
    fun aUsbDeviceThatLeavesIsRemovedAtOnce() =
        runTest {
            val listings = mutableListOf<List<MidiDevice>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { adapter.inputDevices.toList(listings) }
            system.unplug(PIANO.systemId)
            assertEquals(listOf(listOf(piano), emptyList()), listings)
            assertEquals(0, currentTime)
        }

    @Test
    fun aBluetoothDeviceTheAdapterDoesNotKeepIsRemovedAtOnce() =
        runTest {
            val ble = bleAdapter()
            system.plugIn(
                SystemDevice(7, Transport.BLUETOOTH, 1, 1, bluetoothAddress = BLE_ADDRESS, name = "Acme MIDI"),
            )
            assertTrue(ble.isListed())
            system.unplug(7)
            assertFalse(ble.isListed())
        }

    @Test
    fun cancellingWhileWaitingForTheReopenClosesTheInputOnce() =
        runTest {
            val ble = connectedBleAdapter()
            val reader =
                backgroundScope.launch(
                    UnconfinedTestDispatcher(testScheduler),
                ) { ble.events(BLE_PIANO).collect {} }
            system.opensSucceed = false
            system.dropBluetooth(BLE_ADDRESS)
            advanceTimeBy(1_000)
            reader.cancelAndJoin()
            assertEquals(1, system.inputsClosed.get())
        }

    @Test
    fun aDeviceThatIsBackButCannotBeReopenedEndsTheFlow() =
        runTest {
            val ble = connectedBleAdapter()
            var completed = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                ble.events(BLE_PIANO).toList()
                completed = true
            }
            system.inputsFail = true
            system.dropBluetooth(BLE_ADDRESS)
            system.devices.first { list -> list.any { it.bluetoothAddress == BLE_ADDRESS } }
            assertTrue(completed)
        }

    private companion object {
        const val BLE_ADDRESS = "AA:BB:CC:DD:EE:01"
        val BLE_PIANO = MidiDevice("ble:$BLE_ADDRESS", "Acme MIDI")
        val NOTE_ON_C4 = byteArrayOf(0x90.toByte(), 60, 100)
        val SUSTAIN_DOWN = byteArrayOf(0xB0.toByte(), 64, 127)
    }
}
