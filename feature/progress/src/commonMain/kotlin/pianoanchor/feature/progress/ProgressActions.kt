package pianoanchor.feature.progress

/** What the user can do on the [ProgressScreen]; the composition root wires these to the ViewModel and navigation. */
public data class ProgressActions(
    val onToday: () -> Unit = {},
    val onSongs: () -> Unit = {},
    val onPianos: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onPlay: (Take) -> Unit = {},
    val onStop: () -> Unit = {},
    val onRetry: () -> Unit = {},
)
