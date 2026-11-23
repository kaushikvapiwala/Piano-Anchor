package pianoanchor.feature.lesson

import androidx.compose.runtime.Composable

/** Whether the system asks for less motion (Android: animations turned off); card animations then hold one frame. */
@Composable
internal expect fun rememberReduceMotion(): Boolean
