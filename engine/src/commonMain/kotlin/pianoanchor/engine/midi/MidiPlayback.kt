package pianoanchor.engine.midi

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.Tone
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.lesson.PedalCue

private const val CHANNEL = 0
private const val PEDAL_FULLY_DOWN = 127

/** What goes first when messages share a millisecond: the pedal goes down before the notes, up after them. */
private const val PEDAL_DOWN_FIRST = 0
private const val NOTE_OFF_NEXT = 1
private const val NOTE_ON_NEXT = 2
private const val PEDAL_UP_LAST = 3

/** Whether this port currently lists [device] as an output (a device seen only as an input can't be played on). */
public suspend fun MidiOutputPort.canPlayOn(device: MidiDevice): Boolean =
    outputDevices.first().any { it.id == device.id }

/** One message of a playback (a NoteOn, a NoteOff or a pedal move), [atMs] after it started; [order] breaks ties. */
private class Action(
    val atMs: Long,
    val order: Int,
    val message: MidiEvent,
)

private fun pedalMessage(down: Boolean) =
    ControlChange(0, CHANNEL, SUSTAIN_CONTROLLER, if (down) PEDAL_FULLY_DOWN else 0)

/**
 * Plays [tones] on [device]: a NoteOn (the tone's velocity, else `Tunables.PLAYBACK_VELOCITY`; channel 0) at each
 * tone's startMs and a NoteOff at startMs + lengthMs, timed with delay() from the call. Each of [pedal]'s cues sends
 * the sustain pedal (controller 64) as 127 (down) or 0 (up) at its time. Returns when the last message is sent.
 * At the same time a pedal-down goes first, then a NoteOff, then a NoteOn (so a repeated note is struck again
 * cleanly), then a pedal-up.
 * Cancelled or failed midway, it sends a NoteOff (in NonCancellable) for every key still down and lifts a pedal still
 * down, stopping quietly at the first [MidiDeviceUnavailableException], then rethrows.
 * [onStruck] is called after each NoteOn that was sent, so a caller whose device fails midway knows how many
 * tones began.
 * @throws MidiDeviceUnavailableException when the device goes away while sending.
 */
public suspend fun MidiOutputPort.playTones(
    device: MidiDevice,
    tones: List<Tone>,
    pedal: List<PedalCue> = emptyList(),
    onStruck: () -> Unit = {},
) {
    val actions =
        (
            tones.flatMap {
                listOf(
                    Action(
                        it.startMs,
                        NOTE_ON_NEXT,
                        NoteOn(0, CHANNEL, it.note, it.velocity ?: Tunables.PLAYBACK_VELOCITY),
                    ),
                    Action(it.startMs + it.lengthMs, NOTE_OFF_NEXT, NoteOff(0, CHANNEL, it.note, 0)),
                )
            } +
                pedal.map { Action(it.atMs, if (it.down) PEDAL_DOWN_FIRST else PEDAL_UP_LAST, pedalMessage(it.down)) }
        ).sortedWith(compareBy({ it.atMs }, { it.order }))
    val down = HashSet<MidiNote>()
    var pedalDown = false
    var at = 0L
    try {
        for (action in actions) {
            delay(action.atMs - at)
            at = action.atMs
            send(device, MidiEncoder.encode(action.message))
            val message = action.message
            if (message is NoteOn) {
                down += message.note
                onStruck()
            } else if (message is NoteOff) {
                down -= message.note
            } else {
                pedalDown = action.order == PEDAL_DOWN_FIRST
            }
        }
    } finally {
        withContext(NonCancellable) {
            try {
                for (note in down) send(device, MidiEncoder.encode(NoteOff(0, CHANNEL, note, 0)))
                if (pedalDown) send(device, MidiEncoder.encode(pedalMessage(false)))
            } catch (_: MidiDeviceUnavailableException) {
                // Gone: nothing is sounding, and the other keys need no release either.
            }
        }
    }
}
