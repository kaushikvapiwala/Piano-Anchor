package pianoanchor.core.model

/**
 * One MIDI message received from an instrument.
 * [timestampNanos] is in the monotonic clock domain of `ClockPort.nowNanos()`; only differences are meaningful.
 */
public sealed interface MidiEvent {
    public val timestampNanos: Long
}

/** Valid range of a MIDI channel as sent on the wire (shown to people as 1–16). */
private val CHANNEL_RANGE: IntRange = 0..15

/** Valid range of a MIDI data byte. */
private val DATA_RANGE: IntRange = 0..127

/** A key went down. [channel] is the wire value 0–15 (shown to people as 1–16); [velocity] is 1–127. */
public data class NoteOn(
    override val timestampNanos: Long,
    val channel: Int,
    val note: MidiNote,
    val velocity: Int,
) : MidiEvent {
    init {
        require(channel in CHANNEL_RANGE) { "channel must be in $CHANNEL_RANGE, was $channel" }
        require(velocity in VELOCITY_RANGE) { "velocity must be in $VELOCITY_RANGE, was $velocity" }
    }

    public companion object {
        public val VELOCITY_RANGE: IntRange = 1..127
    }
}

/** A key came up. [velocity] is the release velocity, 0–127. */
public data class NoteOff(
    override val timestampNanos: Long,
    val channel: Int,
    val note: MidiNote,
    val velocity: Int,
) : MidiEvent {
    init {
        require(channel in CHANNEL_RANGE) { "channel must be in $CHANNEL_RANGE, was $channel" }
        require(velocity in DATA_RANGE) { "velocity must be in $DATA_RANGE, was $velocity" }
    }
}

/** A controller moved, e.g. the sustain pedal (controller 64). [controller] and [value] are 0–127. */
public data class ControlChange(
    override val timestampNanos: Long,
    val channel: Int,
    val controller: Int,
    val value: Int,
) : MidiEvent {
    init {
        require(channel in CHANNEL_RANGE) { "channel must be in $CHANNEL_RANGE, was $channel" }
        require(controller in DATA_RANGE) { "controller must be in $DATA_RANGE, was $controller" }
        require(value in DATA_RANGE) { "value must be in $DATA_RANGE, was $value" }
    }
}

/** A complete System Exclusive message. [payload] holds the bytes between 0xF0 and 0xF7 (exclusive), each 0–127. */
public data class SysEx(
    override val timestampNanos: Long,
    val payload: List<Int>,
) : MidiEvent {
    init {
        payload.forEach { require(it in DATA_RANGE) { "payload byte must be in $DATA_RANGE, was $it" } }
    }
}
