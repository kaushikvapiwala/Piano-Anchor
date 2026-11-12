package pianoanchor.testing

import pianoanchor.core.model.MidiDevice
import pianoanchor.core.ports.MidiInputPort

class FakePianoInputContractTest : MidiInputPortContractTest() {
    override fun createHarness(): InputHarness =
        object : InputHarness {
            private val piano = FakePiano()
            override val port: MidiInputPort = piano
            override val device: MidiDevice = piano.device

            override fun deliver(bytes: ByteArray) = piano.play(bytes)

            override fun unplug() = piano.unplug()
        }
}
