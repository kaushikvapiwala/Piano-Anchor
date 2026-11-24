package pianoanchor.engine.midi

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiTransport
import pianoanchor.core.model.SysEx
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.NANOS_PER_MILLI
import pianoanchor.engine.Tunables
import pianoanchor.engine.identity.IdentityCodec

private const val HALF = 2

/**
 * Half the smallest Identity Request round trip to [device], in nanoseconds, or null when the piano never answered
 * (`docs/algorithms/latency-calibration.md`). It first sends one warm-up request and waits up to
 * `Tunables.CALIBRATION_PING_TIMEOUT_MS` for a reply, because the input may still be opening and lose the first
 * reply; whether that reply comes or not, it is not counted and does not end the measurement. Then it sends
 * `Tunables.CALIBRATION_PING_COUNT` timed requests one after another and times each from the moment its send
 * returned on [clock] to the reply's timestamp; replies queued before a request are discarded and a reply stamped no
 * later than its send is ignored. The first timed request that gets no reply within the timeout ends it with null,
 * as does a [device] without an output or any send that fails with [MidiDeviceUnavailableException]. The reader it
 * opens on [input] is closed before this returns, so the caller can open the input next.
 */
public suspend fun measureLinkLatencyNanos(
    device: MidiDevice,
    input: MidiInputPort,
    output: MidiOutputPort,
    clock: ClockPort,
): Long? {
    if (!output.canPlayOn(device)) return null
    return coroutineScope {
        val replies = Channel<Long>(Channel.UNLIMITED)
        val reader =
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    input.events(device).collect { event ->
                        if (event is SysEx && IdentityCodec.parseReply(event) != null) {
                            replies.trySend(event.timestampNanos)
                        }
                    }
                } catch (_: MidiDeviceUnavailableException) {
                    // Not listed as an input: nothing will answer.
                } finally {
                    replies.close()
                }
            }
        try {
            val roundTrips = ArrayList<Long>()
            pingOnce(device, output, replies, clock) // warm-up: the input may not be open yet
            repeat(Tunables.CALIBRATION_PING_COUNT) {
                roundTrips += pingOnce(device, output, replies, clock) ?: return@coroutineScope null
            }
            roundTrips.min() / HALF
        } catch (_: MidiDeviceUnavailableException) {
            null
        } finally {
            reader.cancelAndJoin()
        }
    }
}

/**
 * One round trip in nanoseconds, or null if no usable reply came in time. Replies already queued are dropped first and
 * the clock is read right after the send returns.
 * @throws MidiDeviceUnavailableException if the send fails.
 */
private suspend fun pingOnce(
    device: MidiDevice,
    output: MidiOutputPort,
    replies: Channel<Long>,
    clock: ClockPort,
): Long? {
    while (replies.tryReceive().isSuccess) {
        // Discard replies to earlier requests.
    }
    output.send(device, IdentityCodec.REQUEST)
    val sent = clock.nowNanos()
    return withTimeoutOrNull(Tunables.CALIBRATION_PING_TIMEOUT_MS) {
        var roundTrip = 0L
        while (roundTrip <= 0) {
            roundTrip = replies.receiveCatching().getOrNull()?.minus(sent) ?: return@withTimeoutOrNull null
        }
        roundTrip
    }
}

/** The link delay assumed for a silent piano: `Tunables.CALIBRATION_BLE_DEFAULT_MS` for Bluetooth, else 0. */
public fun defaultLinkLatencyNanos(transport: MidiTransport): Long =
    when (transport) {
        MidiTransport.BLUETOOTH -> Tunables.CALIBRATION_BLE_DEFAULT_MS * NANOS_PER_MILLI
        MidiTransport.USB, MidiTransport.OTHER -> 0
    }
