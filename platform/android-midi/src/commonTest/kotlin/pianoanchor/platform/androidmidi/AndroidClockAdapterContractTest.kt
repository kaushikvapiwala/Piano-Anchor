package pianoanchor.platform.androidmidi

import pianoanchor.core.ports.ClockPort
import pianoanchor.testing.ClockPortContractTest

class AndroidClockAdapterContractTest : ClockPortContractTest() {
    override fun createSubject(): ClockPort = AndroidClockAdapter()
}
