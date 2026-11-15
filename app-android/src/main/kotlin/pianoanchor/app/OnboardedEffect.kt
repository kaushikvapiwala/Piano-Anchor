package pianoanchor.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.SettingsRepositoryPort

/** Reads once whether the onboarding is finished and tells [onResolved]; an unreadable store counts as finished. */
@Composable
fun OnboardedEffect(onResolved: (onboarded: Boolean) -> Unit) {
    val settings = koinInject<SettingsRepositoryPort>()
    LaunchedEffect(Unit) {
        onResolved(
            try {
                settings.settings.first().onboarded
            } catch (_: StorageUnavailableException) {
                true
            },
        )
    }
}
