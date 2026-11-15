package pianoanchor.core.model

/** A published release of the app: its [version] without a leading `v` (e.g. `0.2.0`) and the web page [pageUrl]. */
public data class Release(
    val version: String,
    val pageUrl: String,
)
