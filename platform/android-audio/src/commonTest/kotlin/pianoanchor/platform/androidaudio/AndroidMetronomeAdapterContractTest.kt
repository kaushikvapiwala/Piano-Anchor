package pianoanchor.platform.androidaudio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MetronomePort
import pianoanchor.testing.MetronomeClick
import pianoanchor.testing.MetronomePortContractTest

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidMetronomeAdapterContractTest : MetronomePortContractTest() {
    override fun createHarness(scope: TestScope): MetronomeHarness =
        object : MetronomeHarness {
            private val clock = virtualClock(scope)
            private val sink = FakeAudioSink(clock)
            private val renderer = ClickRenderer(sink.sampleRate)
            override val metronome: MetronomePort = AndroidMetronomeAdapter(sink, clock, scope.backgroundScope)

            override suspend fun heardUntil(nanos: Long): List<MetronomeClick> {
                scope.advanceTimeBy(nanos / NANOS_PER_MS - scope.currentTime)
                scope.runCurrent()
                return sink.runs
                    .flatMap { run ->
                        sink.onsets(run, renderer).map { (frame, accent) ->
                            MetronomeClick(sink.timeOf(run, frame), accent)
                        }
                    }.filter { it.nanos <= nanos }
            }
        }
}

internal const val NANOS_PER_MS = 1_000_000L

/** A [ClockPort] that reads the test scheduler's virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun virtualClock(scope: TestScope): ClockPort =
    object : ClockPort {
        override fun nowNanos(): Long = scope.currentTime * NANOS_PER_MS
    }
