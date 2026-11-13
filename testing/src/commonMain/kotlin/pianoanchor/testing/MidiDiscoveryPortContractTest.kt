package pianoanchor.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import pianoanchor.core.model.BluetoothUnavailableException.Reason
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.Tunables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behaviour every [MidiDiscoveryPort] adapter must satisfy. Subclass it once per adapter. Runs in virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MidiDiscoveryPortContractTest {
    /** What a contract test needs to drive a discovery adapter from the device side. */
    protected interface DiscoveryHarness {
        val discovery: MidiDiscoveryPort

        /** The input port that lists connected devices (the same adapter as [discovery]). */
        val input: MidiInputPort

        /** The output port that lists connected devices (the same adapter as [discovery]). */
        val output: MidiOutputPort

        /** A device the harness can [advertise]; not listed until connected. */
        val device: MidiDevice

        /** Another advertisable device, distinct from [device]. */
        val other: MidiDevice

        /** [device] starts (or keeps) advertising, so a scan finds it and a connect can open it. */
        suspend fun advertise(device: MidiDevice)

        /** The connected [device]'s link drops once; it keeps advertising, so a reopen succeeds. */
        suspend fun drop(device: MidiDevice)

        /** Bluetooth is switched off from now on. */
        fun bluetoothOff()

        /** The app loses its Bluetooth permissions from now on. */
        fun denyPermission()
    }

    /** Creates the harness; background work of the adapter (reconnecting) runs in [scope]. */
    protected abstract fun createHarness(scope: CoroutineScope): DiscoveryHarness

    private fun TestScope.harness() = createHarness(backgroundScope)

    @Test
    fun scanStartsEmptyAndListsAdvertisedDevices() =
        runTest {
            val h = harness()
            val emissions = mutableListOf<List<MidiDevice>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                h.discovery.scan().collect { emissions.add(it) }
            }
            assertEquals(listOf(emptyList()), emissions)
            h.advertise(h.device)
            runCurrent()
            assertEquals(listOf(h.device), emissions.last())
            h.advertise(h.other)
            runCurrent()
            assertEquals(listOf(h.device, h.other), emissions.last())
        }

    @Test
    fun scanDeduplicates() =
        runTest {
            val h = harness()
            h.advertise(h.device)
            h.advertise(h.device)
            assertEquals(listOf(emptyList(), listOf(h.device)), h.discovery.scan().toList())
        }

    @Test
    fun scanCompletesAfterTimeout() =
        runTest {
            val h = harness()
            val start = currentTime
            h.discovery.scan().toList()
            assertEquals(Tunables.BLE_SCAN_TIMEOUT_MS, currentTime - start)
        }

    @Test
    fun connectListsTheDeviceInInputAndOutput() =
        runTest {
            val h = connected()
            assertTrue(h.device in h.input.inputDevices.first())
            assertTrue(h.device in h.output.outputDevices.first())
        }

    @Test
    fun scanLeavesOutConnectedDevices() =
        runTest {
            val h = connected()
            assertEquals(listOf(emptyList()), h.discovery.scan().toList())
        }

    @Test
    fun connectingAConnectedDeviceAgainKeepsItListed() =
        runTest {
            val h = connected()
            h.discovery.connect(h.device)
            assertTrue(h.device in h.input.inputDevices.first())
        }

    @Test
    fun droppedDeviceIsReconnectedUnderTheSameId() =
        runTest {
            val h = connected()
            h.drop(h.device)
            val dropped = currentTime
            assertFalse(h.device in h.input.inputDevices.first())
            h.input.inputDevices.first { h.device in it }
            assertEquals(Tunables.BLE_RECONNECT_INITIAL_MS, currentTime - dropped)
            assertTrue(h.device in h.output.outputDevices.first())
        }

    @Test
    fun disconnectClosesTheDevice() =
        runTest {
            val h = connected()
            h.discovery.disconnect(h.device)
            assertFalse(h.device in h.input.inputDevices.first())
            assertFalse(h.device in h.output.outputDevices.first())
        }

    @Test
    fun disconnectStopsReconnecting() =
        runTest {
            val h = connected()
            h.drop(h.device)
            h.discovery.disconnect(h.device)
            advanceTimeBy(Tunables.BLE_RECONNECT_GIVE_UP_MS + 1)
            assertFalse(h.device in h.input.inputDevices.first())
        }

    @Test
    fun disconnectingAnUnknownDeviceDoesNothing() =
        runTest {
            val h = harness()
            val before = h.input.inputDevices.first()
            h.discovery.disconnect(h.device)
            assertEquals(before, h.input.inputDevices.first())
        }

    @Test
    fun scanFailsWhenBluetoothOff() =
        runTest {
            val h = harness()
            h.advertise(h.device)
            h.bluetoothOff()
            assertEquals(
                Reason.BLUETOOTH_OFF,
                assertFailsWith<BluetoothUnavailableException> { h.discovery.scan().first() }.reason,
            )
            assertEquals(
                Reason.BLUETOOTH_OFF,
                assertFailsWith<BluetoothUnavailableException> {
                    h.discovery.connect(h.device)
                }.reason,
            )
        }

    @Test
    fun scanFailsWithoutPermission() =
        runTest {
            val h = harness()
            h.advertise(h.device)
            h.denyPermission()
            assertEquals(
                Reason.PERMISSION_DENIED,
                assertFailsWith<BluetoothUnavailableException> { h.discovery.scan().first() }.reason,
            )
            assertEquals(
                Reason.PERMISSION_DENIED,
                assertFailsWith<BluetoothUnavailableException> { h.discovery.connect(h.device) }.reason,
            )
        }

    @Test
    fun connectUnknownDeviceFails() =
        runTest {
            val h = harness()
            assertFailsWith<MidiDeviceUnavailableException> { h.discovery.connect(h.device) }
        }

    @Test
    fun connectNeedsADeviceFoundByAScan() =
        runTest {
            val h = harness()
            h.advertise(h.device)
            assertFailsWith<MidiDeviceUnavailableException> { h.discovery.connect(h.device) }
            assertFalse(h.device in h.input.inputDevices.first())
        }

    /** A harness whose [DiscoveryHarness.device] has been found by a scan and connected. */
    private suspend fun TestScope.connected(): DiscoveryHarness {
        val h = harness()
        h.advertise(h.device)
        h.discovery.scan().first { h.device in it }
        h.discovery.connect(h.device)
        return h
    }
}
