package pianoanchor.testing

import kotlinx.coroutines.test.TestScope
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.ports.MidiInputPort

class FakePianoRideOutContractTest : MidiInputRideOutContractTest() {
    override fun createRideOutHarness(scope: TestScope): RideOutHarness =
        object : RideOutHarness {
            private val piano = FakePiano()
            override val port: MidiInputPort = piano
            override val device: MidiDevice = piano.device

            override suspend fun deliver(bytes: ByteArray) = piano.play(bytes)

            override suspend fun unplug() = piano.unplug()

            override suspend fun dropLink(duringGap: suspend () -> Unit) {
                piano.dropLink()
                duringGap()
            }

            override suspend fun dropForGood() = piano.unplug()
        }
}
