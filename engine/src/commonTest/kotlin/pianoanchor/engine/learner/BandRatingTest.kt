package pianoanchor.engine.learner

import pianoanchor.core.model.Band
import pianoanchor.core.model.Rating
import kotlin.test.Test
import kotlin.test.assertEquals

class BandRatingTest {
    @Test
    fun `keep practising is again`() = assertEquals(Rating.AGAIN, Band.KEEP_PRACTISING.toRating())

    @Test
    fun `good is hard`() = assertEquals(Rating.HARD, Band.GOOD.toRating())

    @Test
    fun `clean is good, never easy`() {
        assertEquals(Rating.GOOD, Band.CLEAN.toRating())
        assertEquals(false, Band.entries.any { it.toRating() == Rating.EASY })
    }
}
