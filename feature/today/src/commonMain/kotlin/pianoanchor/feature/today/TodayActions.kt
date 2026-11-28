package pianoanchor.feature.today

import pianoanchor.core.model.Release

/**
 * What the user can do on the [TodayScreen]; the composition root wires these to the ViewModel and navigation.
 * [onStartSession] starts the session without the card indices in its argument (the trim view's switched-off steps).
 */
public data class TodayActions(
    val onStartSession: (skipped: Set<Int>) -> Unit = {},
    val onSongs: () -> Unit = {},
    val onProgress: () -> Unit = {},
    val onPianos: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSeeRelease: (Release) -> Unit = {},
    val onDismissUpdate: () -> Unit = {},
    val onPauseWeek: () -> Unit = {},
)
