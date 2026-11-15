package pianoanchor.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import pianoanchor.core.model.Settings
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.SettingsRepositoryPort

/** In-memory [SettingsRepositoryPort]: safe to call from any thread, forgets everything when the process ends. */
class FakeSettingsRepository(
    initial: Settings = Settings(),
) : SettingsRepositoryPort {
    private val current = MutableStateFlow(initial)

    /** While true, every call throws [StorageUnavailableException]. */
    var failing: Boolean = false

    override val settings: Flow<Settings> =
        flow {
            failIfFailing("settings")
            emitAll(current)
        }

    override suspend fun update(change: (Settings) -> Settings) {
        failIfFailing("update")
        current.update(change)
    }

    private fun failIfFailing(operation: String) {
        if (failing) throw StorageUnavailableException("$operation failed")
    }
}
