package pianoanchor.platform.androidmidi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiTransport
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.Tunables
import pianoanchor.testing.MidiInputRideOutContractTest

private const val ADDRESS = "AA:BB:CC:DD:EE:01"

/** A Bluetooth piano, the only kind the adapter rides out drops of; connected through the adapter. */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidMidiAdapterInputContractTest : MidiInputRideOutContractTest() {
    override fun createRideOutHarness(scope: TestScope): RideOutHarness =
        object : RideOutHarness {
            private val system = FakeMidiSystem()
            private val adapter = AndroidMidiAdapter(system, scope.backgroundScope)
            override val port: MidiInputPort = adapter
            override val device: MidiDevice =
                MidiDevice(id = "ble:$ADDRESS", name = "Acme MIDI", transport = MidiTransport.BLUETOOTH)

            init {
                system.advertise(BluetoothAdvertisement(ADDRESS, device.name))
                runBlocking { adapter.connect(device) }
            }

            override suspend fun deliver(bytes: ByteArray) = system.deliver(currentSystemId(), bytes)

            override suspend fun unplug() {
                adapter.disconnect(device)
            }

            override suspend fun dropLink(duringGap: suspend () -> Unit) {
                val old = currentSystemId()
                system.dropBluetooth(ADDRESS)
                duringGap()
                system.devices.first { list -> list.any { it.bluetoothAddress == ADDRESS && it.systemId != old } }
            }

            override suspend fun dropForGood() {
                system.opensSucceed = false
                system.dropBluetooth(ADDRESS)
                scope.advanceTimeBy(Tunables.BLE_DROP_GRACE_MS + 1)
            }

            private fun currentSystemId() =
                system.devices.value
                    .single { it.bluetoothAddress == ADDRESS }
                    .systemId
        }
}
