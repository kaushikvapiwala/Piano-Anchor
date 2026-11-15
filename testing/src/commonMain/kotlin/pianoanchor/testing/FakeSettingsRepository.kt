package pianoanchor.testing

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import pianoanchor.core.model.Settings
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.SettingsRepositoryPort
import kotlin.time.Duration

/** In-memory [SettingsRepositoryPort]: safe to call from any thread, forgets everything when the process ends. */
class FakeSettingsRepository(
    initial: Settings = Settings(),
) : SettingsRepositoryPort {
    private val current = MutableStateFlow(initial)

    /** While true, every call throws [StorageUnavailableException]. */
    var failing: Boolean = false

    /** How long a collection of [settings] waits before its first emission, like a slow disk. */
    var readDelay: Duration = Duration.ZERO

    override val settings: Flow<Settings> =
        flow {
            delay(readDelay)
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
