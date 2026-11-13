package pianoanchor.engine.fixture

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reads and writes [Fixture]s as JSONL (`fixtures/SCHEMA.md`): a header line, then one line per message with its
 * time and its bytes as lowercase hex. Lines end with '\n'.
 */
public object FixtureCodec {
    /** The format version this codec writes and reads. */
    public const val SCHEMA: Int = 1

    private val json = Json

    /** The JSONL text of [fixture], ending with a newline. */
    public fun encode(fixture: Fixture): String =
        buildString {
            val header = fixture.header
            appendLine(json.encodeToString(HeaderLine(header.schema, header.device, header.recordedAt, header.clock)))
            fixture.messages.forEach { appendLine(json.encodeToString(MessageLine(it.t, it.bytes.toHex()))) }
        }

    /**
     * Parses [jsonl]; a final newline is optional.
     * @throws FixtureFormatException naming the first bad line (1-based).
     */
    public fun decode(jsonl: String): Fixture {
        val lines = jsonl.removeSuffix("\n").split('\n')
        val header = decodeHeader(lines.first())
        var previous = 0L
        val messages =
            lines.drop(1).mapIndexed { index, text ->
                val lineNumber = index + 2
                decodeMessage(text, lineNumber, previous).also { previous = it.t }
            }
        return Fixture(header, messages)
    }

    private fun decodeHeader(text: String): Fixture.Header {
        fun fail(reason: String): Nothing = throw FixtureFormatException(1, reason)
        val line = parse<HeaderLine>(text) ?: fail("missing header (schema, device, recordedAt, clock)")
        if (line.schema != SCHEMA) fail("unsupported schema ${line.schema}, expected $SCHEMA")
        return Fixture.Header(line.schema, line.device, line.recordedAt, line.clock)
    }

    private fun decodeMessage(
        text: String,
        lineNumber: Int,
        previousT: Long,
    ): Fixture.Message {
        fun fail(reason: String): Nothing = throw FixtureFormatException(lineNumber, reason)
        val line = parse<MessageLine>(text) ?: fail("not a message line {\"t\":…,\"bytes\":\"…\"}")
        if (line.t < 0) fail("t must not be negative, was ${line.t}")
        if (line.t < previousT) fail("t must not decrease, was ${line.t} after $previousT")
        val bytes = line.bytes.hexToBytesOrNull() ?: fail("bytes must be pairs of hex digits, was \"${line.bytes}\"")
        if (bytes.isEmpty()) fail("bytes must not be empty")
        if ((bytes[0].toInt() and BYTE_MASK) < STATUS_MIN) fail("bytes must start with a status byte (80–ff)")
        return Fixture.Message(line.t, bytes)
    }

    private inline fun <reified T> parse(text: String): T? =
        try {
            json.decodeFromString<T>(text)
        } catch (_: SerializationException) {
            null
        }
}

/** Thrown by [FixtureCodec.decode] for malformed input; [line] is the 1-based line number. */
public class FixtureFormatException(
    public val line: Int,
    reason: String,
) : Exception("line $line: $reason")

@Serializable
private data class HeaderLine(
    val schema: Int,
    val device: String,
    val recordedAt: String,
    val clock: String,
)

@Serializable
private data class MessageLine(
    val t: Long,
    val bytes: String,
)

internal fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and BYTE_MASK).toString(HEX).padStart(2, '0') }

private fun String.hexToBytesOrNull(): ByteArray? {
    if (length % 2 != 0 || any { it.digitToIntOrNull(HEX) == null }) return null
    return ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(HEX).toByte() }
}

private const val BYTE_MASK = 0xFF
private const val STATUS_MIN = 0x80
private const val HEX = 16
