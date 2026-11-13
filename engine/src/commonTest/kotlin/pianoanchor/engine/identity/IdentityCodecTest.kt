package pianoanchor.engine.identity

import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.core.model.SysEx
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class IdentityCodecTest {
    private fun reply(vararg payload: Int) =
        IdentityCodec.parseReply(SysEx(timestampNanos = 0, payload = payload.toList()))

    @Test
    fun requestIsTheAllCallIdentityRequest() {
        val expected = byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte())
        assertContentEquals(expected, IdentityCodec.REQUEST)
    }

    @Test
    fun requestIsAFreshCopyEachTime() {
        val first = IdentityCodec.REQUEST
        first[1] = 0
        assertNotSame(first, IdentityCodec.REQUEST)
        assertEquals(0x7E, IdentityCodec.REQUEST[1].toInt())
    }

    @Test
    fun parsesAReplyWithAOneByteManufacturer() {
        assertEquals(
            InstrumentIdentity(listOf(0x41), family = 0x19, model = 0x02, version = listOf(0, 1, 2, 3)),
            reply(0x7E, 0x10, 0x06, 0x02, 0x41, 0x19, 0x00, 0x02, 0x00, 0, 1, 2, 3),
        )
    }

    @Test
    fun parsesAReplyWithAThreeByteManufacturer() {
        assertEquals(
            InstrumentIdentity(listOf(0x00, 0x20, 0x33), family = 1, model = 2, version = listOf(1, 0, 0, 0)),
            reply(0x7E, 0x7F, 0x06, 0x02, 0x00, 0x20, 0x33, 0x01, 0x00, 0x02, 0x00, 1, 0, 0, 0),
        )
    }

    @Test
    fun familyAndModelAreFourteenBitLeastSignificantByteFirst() {
        val identity = reply(0x7E, 0x00, 0x06, 0x02, 0x41, 0x7F, 0x01, 0x7F, 0x7F, 0, 0, 0, 0)
        assertEquals(255, identity?.family)
        assertEquals(16_383, identity?.model)
    }

    @Test
    fun acceptsAnyDeviceIdByte() {
        for (deviceId in listOf(0x00, 0x10, 0x7F)) {
            assertEquals(
                0x41,
                reply(0x7E, deviceId, 0x06, 0x02, 0x41, 0, 0, 0, 0, 0, 0, 0, 0)?.manufacturerId?.single(),
            )
        }
    }

    @Test
    fun ignoresTrailingBytes() {
        assertEquals(
            InstrumentIdentity(listOf(0x41), family = 0, model = 0, version = listOf(9, 9, 9, 9)),
            reply(0x7E, 0x10, 0x06, 0x02, 0x41, 0, 0, 0, 0, 9, 9, 9, 9, 0x55, 0x66),
        )
    }

    @Test
    fun otherSysExIsNull() {
        assertNull(reply(0x7D, 0x10, 0x06, 0x02, 0x41, 0, 0, 0, 0, 0, 0, 0, 0)) // not Universal Non-Real-Time
        assertNull(reply(0x7E, 0x10, 0x07, 0x02, 0x41, 0, 0, 0, 0, 0, 0, 0, 0)) // not General Information
        assertNull(reply(0x7E, 0x7F, 0x06, 0x01)) // an Identity Request, not a reply
        assertNull(reply(0x41, 0x10, 0x42, 0x12)) // vendor SysEx
    }

    @Test
    fun truncatedReplyIsNull() {
        assertNull(reply())
        assertNull(reply(0x7E, 0x10, 0x06))
        assertNull(reply(0x7E, 0x10, 0x06, 0x02))
        assertNull(reply(0x7E, 0x10, 0x06, 0x02, 0x41, 0, 0, 0, 0, 0, 0, 0)) // one version byte short
        assertNull(reply(0x7E, 0x10, 0x06, 0x02, 0x00, 0x20, 0x33, 0, 0, 0, 0, 0, 0, 0)) // 3-byte id, one short
    }
}
