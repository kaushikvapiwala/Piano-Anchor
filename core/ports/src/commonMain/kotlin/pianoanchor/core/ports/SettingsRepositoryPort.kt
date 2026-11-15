package pianoanchor.core.ports

import kotlinx.coroutines.flow.Flow
import pianoanchor.core.model.Settings
import pianoanchor.core.model.StorageUnavailableException

/**
 * Driven port: the learner's [Settings], kept across restarts. Anything never set reads as its default. Throws
 * [StorageUnavailableException] when the store can't be read or written (#55).
 * Contract: `pianoanchor.testing.SettingsRepositoryPortContractTest`.
 */
public interface SettingsRepositoryPort {
    /** The current settings, emitted on collection and again after every [update]. */
    public val settings: Flow<Settings>

    /**
     * Replaces the settings with [change] applied to the current ones. Atomic: concurrent updates apply one after the
     * other, each to the result of the one before, so none is lost.
     */
    public suspend fun update(change: (Settings) -> Settings)
}
