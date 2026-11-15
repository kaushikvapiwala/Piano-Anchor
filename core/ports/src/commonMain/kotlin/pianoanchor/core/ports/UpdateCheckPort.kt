package pianoanchor.core.ports

import pianoanchor.core.model.Release

/**
 * Driven port: the newest published release. One anonymous HTTPS GET; nothing about the learner is sent
 * (`docs/protocols/update-check.md`). The app's only network use, and the learner can turn it off.
 * Contract: `pianoanchor.testing.UpdateCheckPortContractTest`.
 */
public interface UpdateCheckPort {
    /**
     * The latest release, or null when it can't be known (offline, error, rate limit). Never throws, except for
     * cancellation.
     */
    public suspend fun latest(): Release?
}
