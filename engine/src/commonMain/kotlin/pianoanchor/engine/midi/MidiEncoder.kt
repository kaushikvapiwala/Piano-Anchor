package pianoanchor.engine.midi

import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.SysEx

/**
 * Canonical bytes for a [MidiEvent]: the status byte is always present (no running status) and SysEx is framed
 * with 0xF0…0xF7. `MidiParser().feed(encode(event), t)` gives back exactly `listOf(event)` stamped with `t`.
 */
public object MidiEncoder {
    /** The MIDI 1.0 bytes of [event]. */
    public fun encode(event: MidiEvent): ByteArray =
        when (event) {
            is NoteOn -> channelMessage(NOTE_ON, event.channel, event.note.number, event.velocity)
            is NoteOff -> channelMessage(NOTE_OFF, event.channel, event.note.number, event.velocity)
            is ControlChange -> channelMessage(CONTROL_CHANGE, event.channel, event.controller, event.value)
            is SysEx -> (listOf(START_OF_SYSEX) + event.payload + END_OF_SYSEX).toBytes()
        }

    private fun channelMessage(
        kind: Int,
        channel: Int,
        first: Int,
        second: Int,
    ): ByteArray = listOf(kind or channel, first, second).toBytes()

    private fun List<Int>.toBytes() = ByteArray(size) { this[it].toByte() }
}

private const val NOTE_OFF = 0x80
private const val NOTE_ON = 0x90
private const val CONTROL_CHANGE = 0xB0
private const val START_OF_SYSEX = 0xF0
private const val END_OF_SYSEX = 0xF7
