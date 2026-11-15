package pianoanchor

private const val MAJOR_WEIGHT = 10_000
private const val MINOR_WEIGHT = 100
private const val MAX_MINOR_OR_PATCH = 99
private val releaseVersion = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")

/** The versionName of untagged builds. */
const val DEV_VERSION_NAME = "0.0.0-dev"

/** The versionCode of untagged builds. */
const val DEV_VERSION_CODE = 1

/**
 * The Android versionCode of release [versionName] `X.Y.Z`: `X*10000 + Y*100 + Z`, so codes grow with versions.
 * Minor and patch must each be at most 99, or the codes of two releases could collide or go backwards. Parts have no
 * leading zeros, so `0.01.0` can't share a code with `0.1.0`.
 */
fun releaseVersionCode(versionName: String): Int {
    val parts =
        requireNotNull(releaseVersion.matchEntire(versionName)) { "Version must look like X.Y.Z, got '$versionName'." }
    val (major, minor, patch) = parts.destructured
    require(minor.toInt() <= MAX_MINOR_OR_PATCH && patch.toInt() <= MAX_MINOR_OR_PATCH) {
        "Minor and patch of '$versionName' must each be at most $MAX_MINOR_OR_PATCH."
    }
    return major.toInt() * MAJOR_WEIGHT + minor.toInt() * MINOR_WEIGHT + patch.toInt()
}
