package pianoanchor.engine.fixture

/**
 * A recorded MIDI session, stored as JSONL by [FixtureCodec] (format: `fixtures/SCHEMA.md`). The matcher and replay
 * tests feed [messages] through `MidiParser` to get back the events the app saw live.
 */
public data class Fixture(
    val header: Header,
    val messages: List<Message>,
) {
    /**
     * What was recorded and when. [schema] is the format version ([FixtureCodec.SCHEMA]); [device] the piano's
     * display name; [recordedAt] an ISO-8601 UTC timestamp; [clock] the clock domain of [Message.t].
     */
    public data class Header(
        val schema: Int,
        val device: String,
        val recordedAt: String,
        val clock: String = MONOTONIC_NS,
    )

    /**
     * One MIDI message: [t] in nanoseconds since the first message (≥ 0, non-decreasing through the file) and its
     * canonical [bytes] (`MidiEncoder`), which start with a status byte. Compared by byte content.
     */
    public class Message(
        public val t: Long,
        public val bytes: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean = other is Message && t == other.t && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = HASH_MULTIPLIER * t.hashCode() + bytes.contentHashCode()

        override fun toString(): String = "Message(t=$t, bytes=${bytes.toHex()})"
    }
}

/** The clock domain of `ClockPort.nowNanos()`. */
internal const val MONOTONIC_NS = "monotonic-ns"

private const val HASH_MULTIPLIER = 31
