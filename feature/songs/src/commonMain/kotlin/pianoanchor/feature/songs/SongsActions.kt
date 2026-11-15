package pianoanchor.feature.songs

import pianoanchor.engine.content.PieceView

/** What the user can do on the [SongsScreen]; the composition root wires these to the ViewModel and navigation. */
public data class SongsActions(
    val onOpen: (PieceView) -> Unit = {},
    val onToday: () -> Unit = {},
    val onPianos: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onRetry: () -> Unit = {},
)
