package pianoanchor.engine.matcher

import pianoanchor.core.model.ErrorKind
import pianoanchor.core.model.Finding
import pianoanchor.engine.Tunables

/**
 * Whether [finding] stops a timed run so it starts again from the top: a wrong note or a missed note. Timing errors
 * (early, late), held-too-short and the rest are marked and judged in the results, never restart the run.
 */
public fun isRestartFinding(finding: Finding): Boolean =
    finding.kind == ErrorKind.WRONG_NOTE || finding.kind == ErrorKind.MISSED_NOTE

/** Whether [restartsInARow] restarts on one rung are enough to offer the learner a slower rung. */
public fun offersSlowerRung(restartsInARow: Int): Boolean = restartsInARow >= Tunables.TEMPO_RESTARTS_BEFORE_OFFER
