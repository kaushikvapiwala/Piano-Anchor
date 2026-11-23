package pianoanchor.feature.today

import pianoanchor.core.model.Release
import pianoanchor.engine.session.Card

/** What the user can do on the [TodayScreen]; the composition root wires these to the ViewModel and navigation. */
public data class TodayActions(
    val onStart: (Card) -> Unit = {},
    val onSongs: () -> Unit = {},
    val onPianos: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSeeRelease: (Release) -> Unit = {},
    val onDismissUpdate: () -> Unit = {},
)
