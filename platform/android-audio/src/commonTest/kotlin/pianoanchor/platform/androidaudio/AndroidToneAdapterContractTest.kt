package pianoanchor.platform.androidaudio

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import pianoanchor.core.ports.TonePort
import pianoanchor.testing.TonePortContractTest

class AndroidToneAdapterContractTest : TonePortContractTest() {
    override fun createHarness(scope: TestScope): ToneHarness =
        object : ToneHarness {
            private val sink = FakeAudioSink(virtualClock(scope))
            override val tone: TonePort = AndroidToneAdapter(sink, StandardTestDispatcher(scope.testScheduler))
            override val sounding: Boolean get() = sink.playing
        }
}
