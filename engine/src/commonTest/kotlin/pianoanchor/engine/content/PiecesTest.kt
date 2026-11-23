package pianoanchor.engine.content

import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.PassageHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class PiecesTest {
    private fun list(history: Map<String, PassageHistory> = emptyMap()) = Pieces.list(Curriculum, history)

    private fun piece(
        id: String,
        views: List<PieceView>,
    ) = views.first { it.passage.id == "builtin:$id" }

    private fun played(
        id: String,
        vararg rungs: TempoRung,
        cleanWait: Boolean = false,
    ): Pair<String, PassageHistory> {
        val passage = checkNotNull(Curriculum.passage("builtin:$id"))
        return passage.id to
            PassageHistory(Instant.fromEpochSeconds(1), rungs.mapNotNull { it.bpm(passage) }.toSet(), cleanWait)
    }

    @Test
    fun aPieceWhoseSkillsAreUnlearnedIsNewNotLocked() {
        val views = list()
        assertEquals(PieceStatus.NEW, piece("lightly-row-with-chords", views).status)
        assertEquals(PieceStatus.NEW, piece("four-chord-walk", views).status)
    }

    @Test
    fun newWhenNeverPlayed() {
        val view = piece("lightly-row", list())
        assertEquals(PieceStatus.NEW, view.status)
        assertEquals(0, view.cleanRungs)
        assertEquals(5, view.totalRungs)
        assertEquals(TempoRung.WAIT, view.nextRung)
        val empty = mapOf("builtin:lightly-row" to PassageHistory(lastPlayed = null, cleanTempos = emptySet()))
        assertEquals(PieceStatus.NEW, piece("lightly-row", list(empty)).status)
    }

    @Test
    fun inProgressWithCleanRungsCounted() {
        val views = list(mapOf(played("lightly-row", TempoRung.P60, TempoRung.P70, cleanWait = true)))
        val view = piece("lightly-row", views)
        assertEquals(PieceStatus.IN_PROGRESS, view.status)
        assertEquals(3, view.cleanRungs)
    }

    @Test
    fun masteredAtTheTopRung() {
        val view =
            piece("ode-to-joy", list(mapOf(played("ode-to-joy", TempoRung.P60, TempoRung.P100))))
        assertEquals(PieceStatus.MASTERED, view.status)
        assertEquals(2, view.cleanRungs)
        assertNull(view.nextRung)
    }

    @Test
    fun nextRungIsAboveTheHighestClean() {
        val view = piece("ode-to-joy", list(mapOf(played("ode-to-joy", TempoRung.P60, TempoRung.P85))))
        assertEquals(TempoRung.P100, view.nextRung)
        val waitOnly = piece("ode-to-joy", list(mapOf(played("ode-to-joy", cleanWait = true))))
        assertEquals(TempoRung.P60, waitOnly.nextRung)
    }

    @Test
    fun playedWithNothingCleanIsInProgressAtWait() {
        val view = piece("ode-to-joy", list(mapOf(played("ode-to-joy"))))
        assertEquals(PieceStatus.IN_PROGRESS, view.status)
        assertEquals(TempoRung.WAIT, view.nextRung)
    }

    @Test
    fun groupedByUnitInCurriculumOrder() {
        val views = list()
        val units = views.map { it.unitTitle }.distinct()
        assertEquals(
            listOf("Five-finger tunes", "Left hand, then both hands", "Chords and songs", "More songs"),
            units,
        )
        assertEquals(views.map { it.unitTitle }, views.map { it.unitTitle }.sortedBy { units.indexOf(it) })
        assertEquals(
            listOf("Lightly Row with chords", "Ode to Joy with chords"),
            views.filter { it.unitTitle == "Chords and songs" }.map { it.passage.title },
        )
        assertEquals(
            listOf("When the Saints Go Marching In", "Four-chord walk"),
            views.filter { it.unitTitle == "More songs" }.map { it.passage.title },
        )
        assertEquals(views.size, views.map { it.passage.id }.toSet().size)
    }
}
