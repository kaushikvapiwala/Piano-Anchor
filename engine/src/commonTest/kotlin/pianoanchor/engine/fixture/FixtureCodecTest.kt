package pianoanchor.engine.fixture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FixtureCodecTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private val header = Fixture.Header(schema = 1, device = "Fake Piano", recordedAt = "2026-11-12T10:00:00Z")
    private val fixture =
        Fixture(
            header,
            listOf(
                Fixture.Message(0, bytes(0x90, 0x3C, 0x64)),
                Fixture.Message(250_000_000, bytes(0x80, 0x3C, 0x40)),
                Fixture.Message(250_000_000, bytes(0xF0, 0x7E, 0xF7)),
            ),
        )
    private val headerLine =
        """{"schema":1,"device":"Fake Piano","recordedAt":"2026-11-12T10:00:00Z","clock":"monotonic-ns"}"""

    /** The header line followed by [messageLines], each ended with '\n'. */
    private fun file(vararg messageLines: String) = (listOf(headerLine) + messageLines).joinToString("") { it + "\n" }

    private fun failureLine(jsonl: String): FixtureFormatException = assertFailsWith { FixtureCodec.decode(jsonl) }

    @Test
    fun encodeWritesHeaderThenOneLinePerMessageWithTrailingNewline() {
        val expected =
            headerLine + "\n" +
                """{"t":0,"bytes":"903c64"}""" + "\n" +
                """{"t":250000000,"bytes":"803c40"}""" + "\n" +
                """{"t":250000000,"bytes":"f07ef7"}""" + "\n"
        assertEquals(expected, FixtureCodec.encode(fixture))
    }

    @Test
    fun encodeOfAFixtureWithoutMessagesIsJustTheHeader() {
        assertEquals(headerLine + "\n", FixtureCodec.encode(Fixture(header, emptyList())))
    }

    @Test
    fun decodeOfEncodeGivesTheSameFixture() {
        assertEquals(fixture, FixtureCodec.decode(FixtureCodec.encode(fixture)))
    }

    @Test
    fun decodeAcceptsUppercaseHexAndNoTrailingNewline() {
        val decoded = FixtureCodec.decode(headerLine + "\n" + """{"t":3,"bytes":"B0407F"}""")
        assertEquals(Fixture(header, listOf(Fixture.Message(3, bytes(0xB0, 0x40, 0x7F)))), decoded)
    }

    @Test
    fun emptyInputIsMissingTheHeader() {
        assertEquals(1, failureLine("").line)
    }

    @Test
    fun messageLineFirstIsMissingTheHeader() {
        val e = failureLine("""{"t":0,"bytes":"903c64"}""" + "\n")
        assertEquals(1, e.line)
        assertTrue("header" in e.message.orEmpty())
    }

    @Test
    fun wrongSchemaIsRejectedOnTheHeaderLine() {
        val e = failureLine(headerLine.replace("\"schema\":1", "\"schema\":2") + "\n")
        assertEquals(1, e.line)
        assertTrue("schema" in e.message.orEmpty())
    }

    @Test
    fun nonHexBytesAreRejectedWithTheirLineNumber() {
        val e = failureLine(file("""{"t":0,"bytes":"903c64"}""", """{"t":1,"bytes":"90zz64"}"""))
        assertEquals(3, e.line)
        assertTrue(e.message.orEmpty().startsWith("line 3: "))
    }

    @Test
    fun oddNumberOfHexDigitsIsRejected() {
        assertEquals(2, failureLine(headerLine + "\n" + """{"t":0,"bytes":"903c6"}""").line)
    }

    @Test
    fun negativeTimeIsRejected() {
        assertEquals(2, failureLine(headerLine + "\n" + """{"t":-1,"bytes":"903c64"}""").line)
    }

    @Test
    fun decreasingTimeIsRejected() {
        val e = failureLine(file("""{"t":10,"bytes":"903c64"}""", """{"t":9,"bytes":"803c40"}"""))
        assertEquals(3, e.line)
    }

    @Test
    fun emptyBytesAreRejected() {
        assertEquals(2, failureLine(headerLine + "\n" + """{"t":0,"bytes":""}""").line)
    }

    @Test
    fun bytesWithoutALeadingStatusByteAreRejected() {
        assertEquals(2, failureLine(headerLine + "\n" + """{"t":0,"bytes":"3c64"}""").line)
    }

    @Test
    fun malformedMessageLineIsRejected() {
        assertEquals(2, failureLine(headerLine + "\n" + "not json").line)
    }

    @Test
    fun messageLineWithoutBytesIsRejected() {
        assertEquals(2, failureLine(file("""{"t":0}""")).line)
    }

    @Test
    fun headerWithoutClockIsRejected() {
        assertEquals(1, failureLine(file().replace(""","clock":"monotonic-ns"""", "")).line)
    }

    @Test
    fun blankLineInsideTheFileIsRejected() {
        assertEquals(2, failureLine(headerLine + "\n\n" + """{"t":0,"bytes":"903c64"}""").line)
    }

    @Test
    fun messagesCompareByByteContent() {
        val a = Fixture.Message(1, bytes(0x90, 0x3C, 0x64))
        assertEquals(a, Fixture.Message(1, bytes(0x90, 0x3C, 0x64)))
        assertEquals(a.hashCode(), Fixture.Message(1, bytes(0x90, 0x3C, 0x64)).hashCode())
        assertNotEquals(a, Fixture.Message(2, bytes(0x90, 0x3C, 0x64)))
        assertNotEquals(a, Fixture.Message(1, bytes(0x90, 0x3C, 0x65)))
        assertNotEquals<Any>(a, "not a message")
        assertEquals("Message(t=1, bytes=903c64)", a.toString())
    }
}
