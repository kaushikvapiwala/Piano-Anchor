package pianoanchor.platform.androidmidi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.testing.MidiInputPortContractTest

class AndroidMidiAdapterInputContractTest : MidiInputPortContractTest() {
    override fun createHarness(): InputHarness =
        object : InputHarness {
            private val system = FakeMidiSystem(PIANO)
            override val port: MidiInputPort = AndroidMidiAdapter(system, CoroutineScope(Job()))
            override val device: MidiDevice = MidiDevice(id = "usb:Acme/Stage Piano/SN1", name = "Acme Stage Piano")

            override suspend fun deliver(bytes: ByteArray) = system.deliver(PIANO.systemId, bytes)

            override suspend fun unplug() = system.unplug(PIANO.systemId)
        }
}
