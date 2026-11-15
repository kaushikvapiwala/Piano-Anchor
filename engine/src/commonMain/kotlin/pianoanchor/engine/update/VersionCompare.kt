package pianoanchor.engine.update

private val VERSION = Regex("""v?(\d{1,9})\.(\d{1,9})\.(\d{1,9})""")

/**
 * True when [candidate] is a higher version than [current]; false when equal or lower; null when either is malformed.
 * Versions are `X.Y.Z` of plain numbers (up to nine digits each) with an optional leading `v` (a release tag such as
 * `v0.2.0`), compared by major, then minor, then patch. Anything else (a pre-release suffix, fewer parts) is malformed.
 */
public fun isNewer(
    candidate: String,
    current: String,
): Boolean? {
    val newer = parts(candidate)
    val installed = parts(current)
    if (newer == null || installed == null) return null
    val firstDifference = newer.zip(installed).firstOrNull { (a, b) -> a != b }
    return firstDifference != null && firstDifference.first > firstDifference.second
}

private fun parts(version: String): List<Int>? {
    val match = VERSION.matchEntire(version) ?: return null
    return match.groupValues.drop(1).map { it.toInt() }
}
