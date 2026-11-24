package pianoanchor.engine.midi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.MidiTransport
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.NANOS_PER_MILLI
import pianoanchor.engine.Tunables
import pianoanchor.testing.FakePiano
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LinkLatencyTest {
    private val piano = FakePiano()
    private val ms = NANOS_PER_MILLI
    private val identityReply = intArrayOf(0xF0, 0x7E, 0x10, 0x06, 0x02, 0x41, 0x19, 0x00, 0x02, 0x00, 0, 1, 2, 3, 0xF7)
    private val otherSysEx = intArrayOf(0xF0, 0x7E, 0x10, 0x06, 0x03, 0xF7)

    private fun bytes(values: IntArray) = values.map { it.toByte() }.toByteArray()

    /** Counts readers that are open: the input must be free again when the measurement returns. */
    private class CountingInput(
        private val inner: MidiInputPort,
    ) : MidiInputPort by inner {
        var open = 0
        var opened = 0

        override fun events(device: MidiDevice): Flow<MidiEvent> =
            inner
                .events(device)
                .onStart {
                    open++
                    opened++
                }.onCompletion { open-- }
    }

    /** Answers the n-th request after `delays[n]` ms (as seen by the clock), each preceded by [first] if given. */
    private fun answerAfter(
        delays: List<Long>,
        first: IntArray? = null,
    ) {
        var n = 0
        piano.replyTo = {
            val delay = delays[n++ % delays.size]
            first?.let { piano.play(bytes(it)) }
            reply(delay)
            null
        }
    }

    /** The piano's Identity Reply, stamped [delayMs] after the request was sent (the clock stands still). */
    private fun reply(delayMs: Long) =
        piano.play(
            bytes(identityReply),
            stampNanos =
                piano.clock.nowNanos() + delayMs * ms,
        )

    private suspend fun measure(input: MidiInputPort = piano) =
        measureLinkLatencyNanos(piano.device, input, piano, piano.clock)

    @Test
    fun halfTheSmallestRoundTripIsTheLinkDelay() =
        runTest {
            answerAfter(listOf(30, 21, 40))
            assertEquals(10_500_000L, measure())
            assertEquals(1 + Tunables.CALIBRATION_PING_COUNT, piano.sent.size) // a warm-up and the timed pings
        }

    @Test
    fun aSilentPianoGivesNullAfterOneTimeout() =
        runTest {
            assertNull(measure())
            assertEquals(2, piano.sent.size) // the warm-up and the first timed ping, which ends it
            assertEquals(2 * Tunables.CALIBRATION_PING_TIMEOUT_MS, currentTime)
        }

    @Test
    fun aPianoThatStopsAnsweringGivesNull() =
        runTest {
            var n = 0
            piano.replyTo = {
                if (n++ < 2) reply(20)
                null
            }
            assertNull(measure())
            assertEquals(3, piano.sent.size)
        }

    @Test
    fun aSysExThatIsNotAnIdentityReplyIsIgnored() =
        runTest {
            answerAfter(listOf(20), first = otherSysEx)
            assertEquals(10_000_000L, measure())
        }

    @Test
    fun aReplyStampedBeforeItsRequestIsIgnoredAndTheWaitContinues() =
        runTest {
            var n = 0
            piano.replyTo = {
                // The first timed ping (the second request) first gets a reply stamped before its send.
                if (n++ == 1) piano.play(bytes(identityReply), stampNanos = piano.clock.nowNanos() - 20 * ms)
                reply(10)
                null
            }
            assertEquals(5_000_000L, measure())
        }

    @Test
    fun aReaderThatConnectsLateStillGivesTheTimedPings() =
        runTest {
            // The first reply (to the warm-up) arrives before the input is open and is lost.
            val slow =
                object : MidiInputPort by piano {
                    override fun events(device: MidiDevice): Flow<MidiEvent> =
                        piano.events(device).onStart { delay(100) }
                }
            answerAfter(listOf(30, 21, 40))
            assertEquals(10_500_000L, measure(slow))
        }

    @Test
    fun aQueuedReplyFromAnEarlierPingIsNotCounted() =
        runTest {
            piano.replyTo = {
                reply(20)
                // A duplicate that would read as a 2 ms round trip to the next ping.
                reply(2)
                null
            }
            assertEquals(10_000_000L, measure())
        }

    @Test
    fun theReaderIsClosedWhenItReturns() =
        runTest {
            val input = CountingInput(piano)
            answerAfter(listOf(20))
            measure(input)
            assertEquals(1, input.opened)
            assertEquals(0, input.open)
            piano.replyTo = { null }
            measure(input)
            assertEquals(0, input.open)
        }

    @Test
    fun aPianoWithoutAnOutputGivesNullAtOnce() =
        runTest {
            piano.inputOnly = true
            assertNull(measure())
            assertEquals(0, currentTime)
            assertEquals(0, piano.sent.size)
        }

    @Test
    fun aSendThatFailsGivesNullAtOnce() =
        runTest {
            piano.failSends = true
            assertNull(measure())
            assertEquals(0, currentTime)
        }

    @Test
    fun aDeviceThatCannotBeReadAsAnInputGivesNull() =
        runTest {
            val unreadable =
                object : MidiInputPort by piano {
                    override fun events(device: MidiDevice): Flow<MidiEvent> =
                        flow { throw MidiDeviceUnavailableException(device.id) }
                }
            assertNull(measure(unreadable))
            assertEquals(2, piano.sent.size)
            assertEquals(0, currentTime)
        }

    @Test
    fun otherMessagesOnTheInputAreIgnored() =
        runTest {
            piano.replyTo = {
                piano.press(MidiNote(60))
                reply(20)
                null
            }
            assertEquals(10_000_000L, measure())
        }

    @Test
    fun theDefaultLinkDelayIsFifteenMillisecondsOnBluetoothAndZeroElsewhere() {
        assertEquals(15 * ms, defaultLinkLatencyNanos(MidiTransport.BLUETOOTH))
        assertEquals(0, defaultLinkLatencyNanos(MidiTransport.USB))
        assertEquals(0, defaultLinkLatencyNanos(MidiTransport.OTHER))
    }
}
