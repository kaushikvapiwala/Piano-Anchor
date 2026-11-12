package pianoanchor.engine.midi

import pianoanchor.core.model.ControlChange
import pianoanchor.core.model.MidiEvent
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOff
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.SysEx
import pianoanchor.engine.Tunables

/**
 * Stateful MIDI 1.0 byte-stream parser. Not thread-safe: one instance per input connection, fed from one thread.
 */
public class MidiParser {
    private var status = NO_STATUS
    private var dataCount = 0
    private var firstData = 0
    private var inSysEx = false
    private var sysExOverflow = false
    private val sysExBytes = ArrayList<Int>()

    /**
     * Parses `bytes[offset until offset + count]`, all received at [timestampNanos], and returns the events
     * completed by these bytes, in stream order. Partial messages are kept for the next call.
     * @throws IllegalArgumentException if the range is outside [bytes].
     */
    public fun feed(
        bytes: ByteArray,
        timestampNanos: Long,
        offset: Int = 0,
        count: Int = bytes.size - offset,
    ): List<MidiEvent> {
        require(offset >= 0 && count >= 0 && count <= bytes.size - offset) {
            "range offset=$offset count=$count is outside array of size ${bytes.size}"
        }
        val events = ArrayList<MidiEvent>()
        for (i in offset until offset + count) {
            val b = bytes[i].toInt() and BYTE_MASK
            when {
                b == SYSTEM_RESET -> reset()
                b >= REAL_TIME_MIN -> Unit
                b >= STATUS_MIN -> onStatus(b, timestampNanos, events)
                else -> onData(b, timestampNanos, events)
            }
        }
        return events
    }

    /** Drops running status and any partial message, e.g. after a reconnect. */
    public fun reset() {
        status = NO_STATUS
        dataCount = 0
        inSysEx = false
        sysExOverflow = false
        sysExBytes.clear()
    }

    private fun onStatus(
        b: Int,
        timestampNanos: Long,
        events: MutableList<MidiEvent>,
    ) {
        if (b == END_OF_SYSEX && inSysEx && !sysExOverflow) events.add(SysEx(timestampNanos, sysExBytes.toList()))
        endSysEx()
        dataCount = 0
        inSysEx = b == START_OF_SYSEX
        status = if (inSysEx || b >= NO_DATA_COMMON_MIN) NO_STATUS else b
    }

    private fun endSysEx() {
        inSysEx = false
        sysExOverflow = false
        sysExBytes.clear()
    }

    private fun onData(
        b: Int,
        timestampNanos: Long,
        events: MutableList<MidiEvent>,
    ) {
        if (inSysEx) {
            collectSysEx(b)
        } else if (status != NO_STATUS) {
            collectChannelData(b, timestampNanos, events)
        }
    }

    private fun collectChannelData(
        b: Int,
        timestampNanos: Long,
        events: MutableList<MidiEvent>,
    ) {
        if (dataCount == 0) firstData = b
        dataCount++
        if (dataCount < dataBytesFor(status)) return
        dataCount = 0
        emit(status, firstData, b, timestampNanos, events)
        if (status >= START_OF_SYSEX) status = NO_STATUS
    }

    private fun collectSysEx(b: Int) {
        if (sysExOverflow) return
        if (sysExBytes.size >= Tunables.SYSEX_MAX_BYTES) {
            sysExOverflow = true
            sysExBytes.clear()
        } else {
            sysExBytes.add(b)
        }
    }

    private fun dataBytesFor(status: Int): Int =
        when (status and KIND_MASK) {
            PROGRAM_CHANGE, CHANNEL_PRESSURE -> 1
            SYSTEM -> if (status == SONG_POSITION) 2 else 1
            else -> 2
        }

    private fun emit(
        status: Int,
        first: Int,
        second: Int,
        timestampNanos: Long,
        events: MutableList<MidiEvent>,
    ) {
        val channel = status and CHANNEL_MASK
        when (status and KIND_MASK) {
            NOTE_OFF -> {
                events.add(NoteOff(timestampNanos, channel, MidiNote(first), second))
            }

            NOTE_ON -> {
                events.add(
                    if (second == 0) {
                        NoteOff(timestampNanos, channel, MidiNote(first), NO_RELEASE_VELOCITY)
                    } else {
                        NoteOn(timestampNanos, channel, MidiNote(first), second)
                    },
                )
            }

            CONTROL_CHANGE -> {
                events.add(ControlChange(timestampNanos, channel, first, second))
            }
        }
    }
}

private const val NO_STATUS = -1
private const val BYTE_MASK = 0xFF
private const val CHANNEL_MASK = 0x0F
private const val KIND_MASK = 0xF0
private const val STATUS_MIN = 0x80
private const val REAL_TIME_MIN = 0xF8
private const val SYSTEM_RESET = 0xFF
private const val NOTE_OFF = 0x80
private const val NOTE_ON = 0x90
private const val CONTROL_CHANGE = 0xB0
private const val PROGRAM_CHANGE = 0xC0
private const val CHANNEL_PRESSURE = 0xD0
private const val SYSTEM = 0xF0
private const val START_OF_SYSEX = 0xF0
private const val SONG_POSITION = 0xF2
private const val END_OF_SYSEX = 0xF7
private const val NO_RELEASE_VELOCITY = 64
private const val NO_DATA_COMMON_MIN = 0xF4
