package pianoanchor.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MetronomePort

private const val NANOS_PER_MS = 1_000_000L

@OptIn(ExperimentalCoroutinesApi::class)
class FakeMetronomeContractTest : MetronomePortContractTest() {
    override fun createHarness(scope: TestScope): MetronomeHarness =
        object : MetronomeHarness {
            private val fake =
                FakeMetronome(
                    object : ClockPort {
                        override fun nowNanos(): Long = scope.currentTime * NANOS_PER_MS
                    },
                )
            override val metronome: MetronomePort = fake

            override suspend fun heardUntil(nanos: Long): List<MetronomeClick> {
                scope.advanceTimeBy(nanos / NANOS_PER_MS - scope.currentTime)
                scope.runCurrent()
                return fake.clicksUntil(nanos)
            }
        }
}
