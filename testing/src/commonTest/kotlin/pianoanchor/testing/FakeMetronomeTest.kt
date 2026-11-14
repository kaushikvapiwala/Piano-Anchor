package pianoanchor.testing

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FakeMetronomeTest {
    @Test
    fun runningIsTheCurrentPatternOrNull() =
        runTest {
            val metronome = FakeMetronome()
            assertNull(metronome.running.value)
            metronome.start(10, 20, 3)
            assertEquals(FakeMetronome.Pattern(10, 20, 3), metronome.running.value)
            metronome.stop()
            assertNull(metronome.running.value)
        }

    @Test
    fun clicksUntilSkipsBeatsBeforeThePatternStartedKeepingThePhase() =
        runTest {
            val clock = FakeClock(nanos = 45)
            val metronome = FakeMetronome(clock)
            metronome.start(firstBeatNanos = 0, beatIntervalNanos = 10, beatsPerBar = 3)
            assertEquals(
                listOf(MetronomeClick(50, false), MetronomeClick(60, true), MetronomeClick(70, false)),
                metronome.clicksUntil(70),
            )
        }

    @Test
    fun aSecondStopKeepsTheFirstStopTime() =
        runTest {
            val clock = FakeClock()
            val metronome = FakeMetronome(clock)
            metronome.start(firstBeatNanos = 0, beatIntervalNanos = 10, beatsPerBar = 1)
            clock.advanceNanos(15)
            metronome.stop()
            clock.advanceNanos(100)
            metronome.stop()
            assertEquals(listOf(MetronomeClick(0, true), MetronomeClick(10, true)), metronome.clicksUntil(200))
        }

    @Test
    fun aBeatAtTheMomentThePatternStartsIsPlayed() =
        runTest {
            val metronome = FakeMetronome(FakeClock(nanos = 40))
            metronome.start(firstBeatNanos = 0, beatIntervalNanos = 10, beatsPerBar = 1)
            assertEquals(listOf(MetronomeClick(40, true)), metronome.clicksUntil(40))
        }
}
