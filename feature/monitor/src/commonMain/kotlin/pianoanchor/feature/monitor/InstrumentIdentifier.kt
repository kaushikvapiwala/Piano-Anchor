package pianoanchor.feature.monitor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.SysEx
import pianoanchor.core.ports.InstrumentRepositoryPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.identity.IdentityCodec
import kotlin.time.Clock

/**
 * The monitor's identify-on-connect step (`docs/protocols/midi-identity.md`): asks a piano who it is and stores it in
 * [instruments]. The caller feeds every received message to [offer]. Not thread-safe: call it from one thread.
 */
internal class InstrumentIdentifier(
    private val output: MidiOutputPort,
    private val instruments: InstrumentRepositoryPort,
    private val wallClock: Clock,
) {
    private var reply = CompletableDeferred<InstrumentIdentity>()

    /** Ends the wait of a running [identify] if [event] is an Identity Reply; ignores anything else. */
    fun offer(event: MidiEvent) {
        if (event is SysEx) IdentityCodec.parseReply(event)?.let(reply::complete)
    }

    /**
     * Sends the Identity Request to [device]'s output, waits up to `Tunables.IDENTITY_REPLY_TIMEOUT_MS` for the reply,
     * then stores the instrument and returns the stored record. With no output, a failed send or no reply it stores
     * no new identity, so the record keeps any identity stored before. When the store fails
     * ([StorageUnavailableException]) it returns the instrument as seen now, not saved.
     */
    suspend fun identify(device: MidiDevice): Identified {
        val awaited = CompletableDeferred<InstrumentIdentity>().also { reply = it } // armed before sending
        val identity =
            output.outputDevices.first().firstOrNull { it.id == device.id }?.let { target ->
                try {
                    output.send(target, IdentityCodec.REQUEST)
                    withTimeoutOrNull(Tunables.IDENTITY_REPLY_TIMEOUT_MS) { awaited.await() }
                } catch (_: MidiDeviceUnavailableException) {
                    null
                }
            }
        val now = wallClock.now()
        val seen = Instrument(device.id, device.name, identity, firstSeen = now, lastSeen = now)
        return try {
            instruments.upsert(seen)
            Identified(instruments.get(device.id) ?: seen, saved = true)
        } catch (_: StorageUnavailableException) {
            Identified(seen, saved = false)
        }
    }
}

/** What [InstrumentIdentifier.identify] found: the [instrument] and whether it was [saved] to the store. */
internal data class Identified(
    val instrument: Instrument,
    val saved: Boolean,
)
