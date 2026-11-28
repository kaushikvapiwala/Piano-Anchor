package pianoanchor.ui.shell

import androidx.compose.runtime.Composable

/** The desktop app has no system setting to read: it always animates. */
@Composable
public actual fun rememberReduceMotion(): Boolean = false
