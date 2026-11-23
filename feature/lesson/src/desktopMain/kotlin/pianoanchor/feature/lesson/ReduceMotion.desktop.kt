package pianoanchor.feature.lesson

import androidx.compose.runtime.Composable

/** The desktop app has no system setting to read: it always animates. */
@Composable
internal actual fun rememberReduceMotion(): Boolean = false
