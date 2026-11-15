package pianoanchor.engine

import pianoanchor.core.model.Strictness
import pianoanchor.core.model.StrictnessProfile

/** The timing tolerance and grading bands of this strictness, from [Tunables] (`docs/algorithms/grading.md`). */
public fun Strictness.profile(): StrictnessProfile =
    when (this) {
        Strictness.GENTLE -> {
            StrictnessProfile(
                Tunables.GENTLE_TIMING_TOLERANCE_MS,
                Tunables.GENTLE_TIMING_TOLERANCE_BEAT_FRACTION,
                Tunables.GENTLE_BAND_CLEAN_MIN_ACCURACY,
                Tunables.GENTLE_BAND_GOOD_MIN_ACCURACY,
            )
        }

        Strictness.STANDARD -> {
            StrictnessProfile(
                Tunables.TIMING_TOLERANCE_MS,
                Tunables.TIMING_TOLERANCE_BEAT_FRACTION,
                Tunables.BAND_CLEAN_MIN_ACCURACY,
                Tunables.BAND_GOOD_MIN_ACCURACY,
            )
        }

        Strictness.STRICT -> {
            StrictnessProfile(
                Tunables.STRICT_TIMING_TOLERANCE_MS,
                Tunables.STRICT_TIMING_TOLERANCE_BEAT_FRACTION,
                Tunables.STRICT_BAND_CLEAN_MIN_ACCURACY,
                Tunables.STRICT_BAND_GOOD_MIN_ACCURACY,
            )
        }
    }
