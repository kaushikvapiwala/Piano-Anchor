package pianoanchor.testing

import kotlinx.coroutines.CoroutineScope
import pianoanchor.core.model.BluetoothUnavailableException.Reason
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort

class FakePianoDiscoveryContractTest : MidiDiscoveryPortContractTest() {
    override fun createHarness(scope: CoroutineScope): DiscoveryHarness =
        object : DiscoveryHarness {
            private val piano = FakePiano()
            override val discovery: MidiDiscoveryPort = piano
            override val input: MidiInputPort = piano
            override val output: MidiOutputPort = piano
            override val device = MidiDevice(id = "ble:fake-1", name = "Fake BLE Piano")
            override val other = MidiDevice(id = "ble:fake-2", name = "Other BLE Piano")

            override suspend fun advertise(device: MidiDevice) {
                piano.advertised += device
            }

            override suspend fun drop(device: MidiDevice) = piano.drop(device, scope)

            override fun bluetoothOff() {
                piano.bluetoothProblem = Reason.BLUETOOTH_OFF
            }

            override fun denyPermission() {
                piano.bluetoothProblem = Reason.PERMISSION_DENIED
            }
        }
}
