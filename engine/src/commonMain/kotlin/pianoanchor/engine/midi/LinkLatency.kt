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
 * (`docs/algorithms/latency-calibration.md`). Sends `Tunables.CALIBRATION_PING_COUNT` requests one after another and
 * times each from the send on [clock] to the reply's timestamp; a reply stamped no later than its send is ignored.
 * The first request that gets no reply within `Tunables.CALIBRATION_PING_TIMEOUT_MS` ends it with null, as does a
 * [device] without an output or a send that fails with [MidiDeviceUnavailableException]. The reader it opens on
 * [input] is closed before this returns, so the caller can open the input next.
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
            repeat(Tunables.CALIBRATION_PING_COUNT) {
                val sent = clock.nowNanos()
                roundTrips += pingOnce(device, output, replies, sent) ?: return@coroutineScope null
            }
            roundTrips.min() / HALF
        } finally {
            reader.cancelAndJoin()
        }
    }
}

/** One round trip in nanoseconds, or null if the send failed or no usable reply came in time. */
private suspend fun pingOnce(
    device: MidiDevice,
    output: MidiOutputPort,
    replies: Channel<Long>,
    sent: Long,
): Long? {
    try {
        output.send(device, IdentityCodec.REQUEST)
    } catch (_: MidiDeviceUnavailableException) {
        return null
    }
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
