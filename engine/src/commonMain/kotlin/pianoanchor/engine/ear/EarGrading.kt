package pianoanchor.engine.ear

import pianoanchor.core.model.Band
import pianoanchor.core.model.EarItemOutcome
import pianoanchor.engine.Tunables

/** Rates an ear set. */
public object EarGrading {
    /**
     * CLEAN when every item was FIRST_TRY; GOOD when at least EAR_GOOD_MIN_RIGHT ended right (first try or
     * recovered); else KEEP_PRACTISING. An empty set is KEEP_PRACTISING.
     */
    public fun band(outcomes: List<EarItemOutcome>): Band =
        when {
            outcomes.isNotEmpty() && outcomes.all { it == EarItemOutcome.FIRST_TRY } -> Band.CLEAN
            outcomes.count { it != EarItemOutcome.MISSED } >= Tunables.EAR_GOOD_MIN_RIGHT -> Band.GOOD
            else -> Band.KEEP_PRACTISING
        }
}
