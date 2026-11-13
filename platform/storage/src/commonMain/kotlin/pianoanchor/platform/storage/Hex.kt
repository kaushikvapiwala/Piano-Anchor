package pianoanchor.platform.storage

private const val HEX_RADIX = 16
private const val DIGITS_PER_BYTE = 2

/** Bytes (each 0–255) as lowercase hex, two digits per byte: `[0x41, 0x10]` → `"4110"`. */
internal fun List<Int>.toHex(): String = joinToString("") { it.toString(HEX_RADIX).padStart(DIGITS_PER_BYTE, '0') }

/** The bytes of a string written by [toHex]. */
internal fun String.hexToBytes(): List<Int> = chunked(DIGITS_PER_BYTE).map { it.toInt(HEX_RADIX) }
