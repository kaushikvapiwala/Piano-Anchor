package pianoanchor.platform.androidmidi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiTransport
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.testing.MidiInputPortContractTest

/** A USB piano: it can't ride out a drop, an unplug is final. */
class AndroidMidiAdapterUsbInputContractTest : MidiInputPortContractTest() {
    override fun createHarness(scope: TestScope): InputHarness =
        object : InputHarness {
            private val system = FakeMidiSystem(PIANO)
            override val port: MidiInputPort = AndroidMidiAdapter(system, CoroutineScope(Job()))
            override val device: MidiDevice =
                MidiDevice(id = "usb:Acme/Stage Piano/SN1", name = "Acme Stage Piano", transport = MidiTransport.USB)

            override suspend fun deliver(bytes: ByteArray) = system.deliver(PIANO.systemId, bytes)

            override suspend fun unplug() = system.unplug(PIANO.systemId)
        }
}
