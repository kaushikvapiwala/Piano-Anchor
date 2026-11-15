package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class StrictnessTest {
    @Test
    fun `there are three presets, Standard in the middle`() =
        assertEquals(listOf(Strictness.GENTLE, Strictness.STANDARD, Strictness.STRICT), Strictness.entries)

    @Test
    fun `a profile keeps its fields and compares by value`() {
        val profile = StrictnessProfile(60, 0.125, 0.95, 0.80)

        assertEquals(60, profile.timingToleranceMs)
        assertEquals(0.125, profile.timingToleranceBeatFraction)
        assertEquals(0.95, profile.cleanMinAccuracy)
        assertEquals(0.80, profile.goodMinAccuracy)
        assertEquals(profile, profile.copy())
    }
}
