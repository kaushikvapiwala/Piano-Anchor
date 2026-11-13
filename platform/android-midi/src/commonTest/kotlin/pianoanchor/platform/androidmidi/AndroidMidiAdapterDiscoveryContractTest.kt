package pianoanchor.platform.androidmidi

import kotlinx.coroutines.CoroutineScope
import pianoanchor.core.model.BluetoothUnavailableException.Reason
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.testing.MidiDiscoveryPortContractTest

class AndroidMidiAdapterDiscoveryContractTest : MidiDiscoveryPortContractTest() {
    override fun createHarness(scope: CoroutineScope): DiscoveryHarness =
        object : DiscoveryHarness {
            private val system = FakeMidiSystem(PIANO)
            private val adapter = AndroidMidiAdapter(system, scope)
            override val discovery: MidiDiscoveryPort = adapter
            override val input: MidiInputPort = adapter
            override val output: MidiOutputPort = adapter
            override val device = MidiDevice(id = "ble:AA:BB:CC:DD:EE:01", name = "Acme MIDI")
            override val other = MidiDevice(id = "ble:AA:BB:CC:DD:EE:02", name = "Other MIDI")

            override suspend fun advertise(device: MidiDevice) =
                system.advertise(BluetoothAdvertisement(device.id.removePrefix("ble:"), device.name))

            override suspend fun drop(device: MidiDevice) = system.dropBluetooth(device.id.removePrefix("ble:"))

            override fun bluetoothOff() {
                system.bluetoothState = Reason.BLUETOOTH_OFF
            }

            override fun denyPermission() {
                system.bluetoothState = Reason.PERMISSION_DENIED
            }
        }
}
