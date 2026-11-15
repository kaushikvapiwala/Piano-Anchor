package pianoanchor.testing

import pianoanchor.core.model.Release
import pianoanchor.core.ports.UpdateCheckPort

/** In-memory [UpdateCheckPort]: answers [release] (null when none is known) and counts the requests in [calls]. */
class FakeUpdateCheck(
    var release: Release? = null,
) : UpdateCheckPort {
    /** How many times [latest] was called. */
    var calls: Int = 0
        private set

    override suspend fun latest(): Release? {
        calls++
        return release
    }
}
