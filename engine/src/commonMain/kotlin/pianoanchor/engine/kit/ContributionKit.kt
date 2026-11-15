package pianoanchor.engine.kit

import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.engine.fixture.Fixture

/** One contribute-a-piano file (`docs/contribute-a-piano.md`). [identity] is null when the piano did not answer. */
public data class ContributionKit(
    val appVersion: String,
    val identity: InstrumentIdentity?,
    val fixture: Fixture,
)
