package pianoanchor.engine.midi

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Tone
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.Tunables

private const val CHANNEL = 0

/** One NoteOn or NoteOff of a playback, [atMs] after it started. */
private class Action(
    val atMs: Long,
    val note: MidiNote,
    val on: Boolean,
)

/**
 * Plays [tones] on [device]: a NoteOn (velocity `Tunables.PLAYBACK_VELOCITY`, channel 0) at each tone's startMs and a
 * NoteOff at startMs + lengthMs, timed with delay() from the call. Returns when the last NoteOff is sent.
 * At the same time a NoteOff goes before a NoteOn, so a repeated note is struck again cleanly.
 * Cancelled or failed midway, it sends a NoteOff (in NonCancellable) for every key still down, ignoring
 * [MidiDeviceUnavailableException] during that release, then rethrows.
 * @throws MidiDeviceUnavailableException when the device goes away while sending.
 */
public suspend fun MidiOutputPort.playTones(
    device: MidiDevice,
    tones: List<Tone>,
) {
    val actions =
        tones
            .flatMap {
                listOf(Action(it.startMs, it.note, true), Action(it.startMs + it.lengthMs, it.note, false))
            }.sortedWith(compareBy({ it.atMs }, { it.on }))
    val down = HashSet<MidiNote>()
    var at = 0L
    try {
        for (action in actions) {
            delay(action.atMs - at)
            at = action.atMs
            val message =
                if (action.on) {
                    NoteOn(0, CHANNEL, action.note, Tunables.PLAYBACK_VELOCITY)
                } else {
                    NoteOff(0, CHANNEL, action.note, 0)
                }
            send(device, MidiEncoder.encode(message))
            if (action.on) down += action.note else down -= action.note
        }
    } finally {
        withContext(NonCancellable) {
            for (note in down) {
                try {
                    send(device, MidiEncoder.encode(NoteOff(0, CHANNEL, note, 0)))
                } catch (_: MidiDeviceUnavailableException) {
                    // Already gone: nothing is sounding.
                }
            }
        }
    }
}
