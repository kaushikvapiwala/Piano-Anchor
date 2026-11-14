package pianoanchor.engine.scheduler

/** Number of weights in FSRS-6: w0..w20. */
private const val FSRS6_WEIGHT_COUNT = 21

/**
 * The 21 FSRS-6 weights w0..w20 (what each one does: `docs/algorithms/scheduler.md`).
 *
 * [DEFAULT] is py-fsrs 6.3.3's `DEFAULT_PARAMETERS`.
 */
public data class FsrsParameters(
    val w: List<Double>,
) {
    init {
        require(w.size == FSRS6_WEIGHT_COUNT) { "FSRS-6 needs $FSRS6_WEIGHT_COUNT weights, got ${w.size}" }
    }

    public companion object {
        /**
         * The default weights, trained on a large public review data set.
         *
         * Source: py-fsrs 6.3.3 `fsrs/scheduler.py` `DEFAULT_PARAMETERS` (MIT, open-spaced-repetition).
         */
        public val DEFAULT: FsrsParameters =
            FsrsParameters(
                listOf(
                    0.212,
                    1.2931,
                    2.3065,
                    8.2956,
                    6.4133,
                    0.8334,
                    3.0194,
                    0.001,
                    1.8722,
                    0.1666,
                    0.796,
                    1.4835,
                    0.0614,
                    0.2629,
                    1.6483,
                    0.6014,
                    1.8729,
                    0.5425,
                    0.0912,
                    0.0658,
                    0.1542,
                ),
            )
    }
}
