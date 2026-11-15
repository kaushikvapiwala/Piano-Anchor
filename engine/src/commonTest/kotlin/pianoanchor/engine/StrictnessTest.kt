package pianoanchor.engine

import pianoanchor.core.model.Strictness
import pianoanchor.core.model.StrictnessProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class StrictnessTest {
    @Test
    fun gentleGivesMoreRoomForTimingAndSlips() =
        assertEquals(StrictnessProfile(90, 0.1875, 0.90, 0.75), Strictness.GENTLE.profile())

    @Test
    fun standardIsTodaysBehaviour() =
        assertEquals(
            StrictnessProfile(
                Tunables.TIMING_TOLERANCE_MS,
                Tunables.TIMING_TOLERANCE_BEAT_FRACTION,
                Tunables.BAND_CLEAN_MIN_ACCURACY,
                Tunables.BAND_GOOD_MIN_ACCURACY,
            ),
            Strictness.STANDARD.profile(),
        )

    @Test
    fun standardValuesAreSixtyMsAnEighthOfABeatAndTheOldBands() =
        assertEquals(StrictnessProfile(60, 0.125, 0.95, 0.80), Strictness.STANDARD.profile())

    @Test
    fun strictIsTightOnTimingAndSlips() =
        assertEquals(StrictnessProfile(40, 0.083, 0.98, 0.90), Strictness.STRICT.profile())
}
