package pianoanchor.ui.shell

import androidx.compose.runtime.Composable

/** Whether the system asks for less motion (Android: animations turned off); card animations then hold one frame. */
@Composable
public expect fun rememberReduceMotion(): Boolean
