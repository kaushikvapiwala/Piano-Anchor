package pianoanchor.platform.androidmidi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.testing.MidiOutputPortContractTest

class AndroidMidiAdapterOutputContractTest : MidiOutputPortContractTest() {
    override fun createHarness(): OutputHarness =
        object : OutputHarness {
            private val system = FakeMidiSystem(PIANO)
            override val port: MidiOutputPort = AndroidMidiAdapter(system, CoroutineScope(Job()))
            override val device: MidiDevice = MidiDevice(id = "usb:Acme/Stage Piano/SN1", name = "Acme Stage Piano")
            override val received: List<ByteArray> get() = system.sent

            override suspend fun unplug() = system.unplug(PIANO.systemId)
        }
}
