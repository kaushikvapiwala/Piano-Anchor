package pianoanchor.engine.identity

import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.core.model.SysEx

private const val SYSEX_START = 0xF0
private const val SYSEX_END = 0xF7
private const val UNIVERSAL_NON_REAL_TIME = 0x7E
private const val ALL_CALL = 0x7F
private const val GENERAL_INFORMATION = 0x06
private const val IDENTITY_REQUEST = 0x01
private const val IDENTITY_REPLY = 0x02

/** Payload positions of the reply header: 7E <device id> 06 02. */
private const val SUB_ID_1_INDEX = 2
private const val SUB_ID_2_INDEX = 3
private const val HEADER_SIZE = 4

private const val EXTENDED_ID_PREFIX = 0
private const val SHORT_ID_SIZE = 1
private const val EXTENDED_ID_SIZE = 3
private const val FOURTEEN_BIT_SIZE = 2
private const val VERSION_SIZE = 4
private const val DATA_BITS = 7

/** The MIDI 1.0 Identity Request and Reply (`docs/protocols/midi-identity.md`). */
public object IdentityCodec {
    /** F0 7E 7F 06 01 F7: the Identity Request to all devices (device id 7F). A fresh copy on every read. */
    public val REQUEST: ByteArray
        get() =
            intArrayOf(SYSEX_START, UNIVERSAL_NON_REAL_TIME, ALL_CALL, GENERAL_INFORMATION, IDENTITY_REQUEST, SYSEX_END)
                .map(Int::toByte)
                .toByteArray()

    /**
     * The identity in [event] if its payload is an Identity Reply (7E <any device id> 06 02 …); null for any other
     * SysEx or a reply too short to hold every field. Bytes after the version are ignored.
     */
    public fun parseReply(event: SysEx): InstrumentIdentity? {
        val payload = event.payload
        val isReply =
            payload.size >= HEADER_SIZE &&
                payload[0] == UNIVERSAL_NON_REAL_TIME &&
                payload[SUB_ID_1_INDEX] == GENERAL_INFORMATION &&
                payload[SUB_ID_2_INDEX] == IDENTITY_REPLY
        return if (isReply) payload.identityAfterHeader() else null
    }
}

/** The identity fields after a reply header, or null if the payload is too short to hold them all. */
private fun List<Int>.identityAfterHeader(): InstrumentIdentity? {
    val idSize = if (getOrNull(HEADER_SIZE) == EXTENDED_ID_PREFIX) EXTENDED_ID_SIZE else SHORT_ID_SIZE
    val family = HEADER_SIZE + idSize
    val model = family + FOURTEEN_BIT_SIZE
    val version = model + FOURTEEN_BIT_SIZE
    if (size < version + VERSION_SIZE) return null
    return InstrumentIdentity(
        manufacturerId = subList(HEADER_SIZE, family).toList(),
        family = fourteenBitAt(family),
        model = fourteenBitAt(model),
        version = subList(version, version + VERSION_SIZE).toList(),
    )
}

/** The 14-bit number sent least significant byte first at [index]. */
private fun List<Int>.fourteenBitAt(index: Int): Int = this[index] or (this[index + 1] shl DATA_BITS)
