package pianoanchor.testing

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.Tone
import pianoanchor.core.ports.TonePort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behaviour every [TonePort] adapter must satisfy. Subclass it once per adapter. Runs in virtual time. The cases are
 * literal timelines: tests may use literal values (`config/detekt/detekt.yml`), but this main source set needs the
 * suppression.
 */
@Suppress("MagicNumber")
@OptIn(ExperimentalCoroutinesApi::class)
abstract class TonePortContractTest {
    /** What a contract test needs to play tones and see whether the speaker is sounding. */
    protected interface ToneHarness {
        val tone: TonePort

        /** True while the speaker is sounding something. */
        val sounding: Boolean
    }

    /** Creates the harness; background work of the adapter runs in [scope]. */
    protected abstract fun createHarness(scope: TestScope): ToneHarness

    private val c4 = MidiNote(60)
    private val e4 = MidiNote(64)

    @Test
    fun playingNothingReturnsAtOnce() =
        runTest {
            createHarness(this).tone.play(emptyList())
            assertEquals(0, currentTime)
        }

    @Test
    fun playReturnsOnceTheLastToneHasEnded() =
        runTest {
            createHarness(this).tone.play(listOf(Tone(c4, 0, 300), Tone(e4, 500, 400)))
            assertEquals(currentTime, maxOf(currentTime, 900))
        }

    @Test
    fun theSpeakerSoundsWhilePlayingAndStopsAfterwards() =
        runTest {
            val harness = createHarness(this)
            assertFalse(harness.sounding)
            val job = launch(start = CoroutineStart.UNDISPATCHED) { harness.tone.play(listOf(Tone(c4, 0, 1000))) }
            advanceTimeBy(300)
            runCurrent()
            assertTrue(harness.sounding)
            job.join()
            assertFalse(harness.sounding)
        }

    @Test
    fun cancellingStopsTheSoundAtOnce() =
        runTest {
            val harness = createHarness(this)
            val job = launch { harness.tone.play(listOf(Tone(c4, 0, 1000))) }
            advanceTimeBy(300)
            runCurrent()
            assertTrue(harness.sounding)
            job.cancelAndJoin()
            assertFalse(harness.sounding)
            assertEquals(300, currentTime)
        }

    @Test
    fun aSecondPlayWorksAfterTheFirst() =
        runTest {
            val harness = createHarness(this)
            harness.tone.play(listOf(Tone(c4, 0, 200)))
            val before = currentTime
            harness.tone.play(listOf(Tone(e4, 0, 200)))
            assertEquals(currentTime - before, maxOf(currentTime - before, 200))
            assertFalse(harness.sounding)
        }
}
