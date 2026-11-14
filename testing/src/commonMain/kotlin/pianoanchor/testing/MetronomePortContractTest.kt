package pianoanchor.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import pianoanchor.core.ports.MetronomePort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val NANOS_PER_MS = 1_000_000L

/**
 * Behaviour every [MetronomePort] adapter must satisfy. Subclass it once per adapter. Runs in virtual time: the beat
 * times are the test scheduler's virtual milliseconds in nanoseconds, and beat times keep a few milliseconds away from
 * a pattern change, where an adapter may still be playing audio it queued for the earlier pattern. The cases are
 * literal timelines: tests may use literal values (`config/detekt/detekt.yml`), but this main source set needs the
 * suppression.
 */
@Suppress("MagicNumber")
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MetronomePortContractTest {
    /** What a contract test needs to drive a metronome and hear it. */
    protected interface MetronomeHarness {
        val metronome: MetronomePort

        /** Lets virtual time run until [nanos] and returns every click heard up to then, oldest first. */
        suspend fun heardUntil(nanos: Long): List<MetronomeClick>
    }

    /** Creates the harness; background work of the adapter (writing audio) runs in [scope]. */
    protected abstract fun createHarness(scope: TestScope): MetronomeHarness

    @Test
    fun startRejectsANonPositiveInterval() =
        runTest {
            val metronome = createHarness(this).metronome
            assertFailsWith<IllegalArgumentException> { metronome.start(0, 0, 4) }
            assertFailsWith<IllegalArgumentException> { metronome.start(0, -1, 4) }
        }

    @Test
    fun startRejectsBeatsPerBarBelowOne() =
        runTest {
            val metronome = createHarness(this).metronome
            assertFailsWith<IllegalArgumentException> { metronome.start(0, ms(100), 0) }
        }

    @Test
    fun stopWhenIdleIsANoOp() =
        runTest {
            val harness = createHarness(this)
            harness.metronome.stop()
            harness.metronome.stop()
            assertEquals(emptyList(), harness.heardUntil(ms(100)))
        }

    @Test
    fun startClicksEveryIntervalAndAccentsTheFirstBeatOfEachBar() =
        runTest {
            val harness = createHarness(this)
            harness.metronome.start(ms(100), ms(100), 3)
            assertEquals(
                listOf(click(100, true), click(200), click(300), click(400, true), click(500), click(600)),
                harness.heardUntil(ms(650)),
            )
        }

    @Test
    fun startTwiceReplacesThePattern() =
        runTest {
            val harness = createHarness(this)
            harness.metronome.start(ms(100), ms(100), 4)
            assertEquals(listOf(click(100, true), click(200)), harness.heardUntil(ms(250)))
            harness.metronome.start(ms(400), ms(50), 2)
            assertEquals(
                listOf(click(100, true), click(200), click(400, true), click(450), click(500, true), click(550)),
                harness.heardUntil(ms(580)),
            )
        }

    @Test
    fun stopEndsTheClicks() =
        runTest {
            val harness = createHarness(this)
            harness.metronome.start(ms(100), ms(100), 4)
            harness.heardUntil(ms(250))
            harness.metronome.stop()
            assertEquals(listOf(click(100, true), click(200)), harness.heardUntil(ms(600)))
        }

    private fun ms(millis: Long) = millis * NANOS_PER_MS

    private fun click(
        millis: Long,
        accent: Boolean = false,
    ) = MetronomeClick(ms(millis), accent)
}
