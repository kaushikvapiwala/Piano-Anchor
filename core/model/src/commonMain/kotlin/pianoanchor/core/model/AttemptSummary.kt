package pianoanchor.core.model

import kotlin.time.Instant

/** What the sight-reading staircase needs of a saved attempt: no events or played notes. */
public data class AttemptSummary(
    val id: String,
    val passageId: String,
    val startedAt: Instant,
    val band: Band,
)

/** This attempt without its events and played notes. */
public val Attempt.summary: AttemptSummary get() = AttemptSummary(id, passageId, startedAt, band)
