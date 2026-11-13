package pianoanchor.testing

import pianoanchor.core.model.MidiDevice
import pianoanchor.core.ports.MidiOutputPort

class FakePianoOutputContractTest : MidiOutputPortContractTest() {
    override fun createHarness(): OutputHarness =
        object : OutputHarness {
            private val piano = FakePiano()
            override val port: MidiOutputPort = piano
            override val device: MidiDevice = piano.device
            override val received: List<ByteArray> get() = piano.sent

            override suspend fun unplug() = piano.unplug()
        }
}
