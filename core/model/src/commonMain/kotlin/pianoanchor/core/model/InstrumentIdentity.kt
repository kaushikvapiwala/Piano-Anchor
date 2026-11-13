package pianoanchor.core.model

/** Valid range of one MIDI data byte. */
private val BYTE_RANGE: IntRange = 0..127

/** Valid range of a 14-bit value sent as two data bytes. */
private val FOURTEEN_BIT_RANGE: IntRange = 0..16_383

/** A 3-byte manufacturer id starts with this byte; a 1-byte id never is it. */
private const val EXTENDED_ID_PREFIX = 0

private const val SHORT_ID_SIZE = 1
private const val EXTENDED_ID_SIZE = 3
private const val VERSION_SIZE = 4

/**
 * What a piano says about itself in a MIDI Identity Reply (`docs/protocols/midi-identity.md`).
 * [manufacturerId] is 1 non-zero byte, or 3 bytes starting with 0x00; [family] and [model] are 14-bit numbers
 * (0–16383); [version] is exactly 4 bytes. Every byte is 0–127.
 */
public data class InstrumentIdentity(
    val manufacturerId: List<Int>,
    val family: Int,
    val model: Int,
    val version: List<Int>,
) {
    init {
        require(
            (manufacturerId.size == SHORT_ID_SIZE && manufacturerId[0] != EXTENDED_ID_PREFIX) ||
                (manufacturerId.size == EXTENDED_ID_SIZE && manufacturerId[0] == EXTENDED_ID_PREFIX),
        ) { "manufacturerId must be 1 non-zero byte or 3 bytes starting with 0, was $manufacturerId" }
        require(manufacturerId.all { it in BYTE_RANGE }) { "manufacturerId bytes must be in $BYTE_RANGE" }
        require(family in FOURTEEN_BIT_RANGE) { "family must be in $FOURTEEN_BIT_RANGE, was $family" }
        require(model in FOURTEEN_BIT_RANGE) { "model must be in $FOURTEEN_BIT_RANGE, was $model" }
        require(version.size == VERSION_SIZE) { "version must have $VERSION_SIZE bytes, was ${version.size}" }
        require(version.all { it in BYTE_RANGE }) { "version bytes must be in $BYTE_RANGE" }
    }
}
