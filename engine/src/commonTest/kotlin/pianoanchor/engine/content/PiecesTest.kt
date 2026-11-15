package pianoanchor.engine.content

import kotlinx.datetime.LocalDate
import pianoanchor.core.model.MemoryState
import pianoanchor.core.model.SkillState
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.PassageHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class PiecesTest {
    private val day = LocalDate(2026, 11, 15)

    private fun learned(skillId: String) =
        SkillState(
            skillId = skillId,
            memory = MemoryState(stability = 2.0, difficulty = 5.0),
            introducedDay = day,
            lastReviewDay = day,
            dueDay = day,
            cleanColdChecks = 0,
            recalledColdChecks = 0,
            learned = true,
        )

    private val everySkillLearned = Curriculum.graph.skills.associate { it.id to learned(it.id) }

    private fun list(
        states: Map<String, SkillState> = everySkillLearned,
        history: Map<String, PassageHistory> = emptyMap(),
    ) = Pieces.list(Curriculum, Curriculum.graph, states, history)

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
    fun lockedWhileAPrerequisiteIsNotLearnedNamingTheFirstMissingSkill() {
        val states = everySkillLearned - "chord-changes"
        val lightly = piece("lightly-row-with-chords", list(states))
        assertEquals(PieceStatus.LOCKED, lightly.status)
        assertEquals(Skills.CHORD_MELODY, lightly.waitingFor)
        // chord-inversions and chord-melody both wait for chord-changes; ties in graph order go by id.
        val saints = piece("when-the-saints-go-marching-in", list(states))
        assertEquals(PieceStatus.LOCKED, saints.status)
        assertEquals(Skills.CHORD_INVERSIONS, saints.waitingFor)
        assertEquals(PieceStatus.NEW, piece("four-chord-walk", list(states)).status)
        // A skill that is known but not yet learned blocks like a missing one.
        val started = everySkillLearned + ("chord-changes" to learned("chord-changes").copy(learned = false))
        assertEquals(PieceStatus.LOCKED, piece("lightly-row-with-chords", list(started)).status)
        assertNull(piece("hot-cross-buns", list(states)).waitingFor)
    }

    @Test
    fun newWhenNeverPlayed() {
        val view = piece("lightly-row", list())
        assertEquals(PieceStatus.NEW, view.status)
        assertEquals(0, view.cleanRungs)
        assertEquals(5, view.totalRungs)
        assertEquals(TempoRung.WAIT, view.nextRung)
        assertNull(view.waitingFor)
        val empty = mapOf("builtin:lightly-row" to PassageHistory(lastPlayed = null, cleanTempos = emptySet()))
        assertEquals(PieceStatus.NEW, piece("lightly-row", list(history = empty)).status)
    }

    @Test
    fun inProgressWithCleanRungsCounted() {
        val views = list(history = mapOf(played("lightly-row", TempoRung.P60, TempoRung.P70, cleanWait = true)))
        val view = piece("lightly-row", views)
        assertEquals(PieceStatus.IN_PROGRESS, view.status)
        assertEquals(3, view.cleanRungs)
    }

    @Test
    fun masteredAtTheTopRung() {
        val view =
            piece("ode-to-joy", list(history = mapOf(played("ode-to-joy", TempoRung.P60, TempoRung.P100))))
        assertEquals(PieceStatus.MASTERED, view.status)
        assertEquals(2, view.cleanRungs)
        assertNull(view.nextRung)
    }

    @Test
    fun nextRungIsAboveTheHighestClean() {
        val view = piece("ode-to-joy", list(history = mapOf(played("ode-to-joy", TempoRung.P60, TempoRung.P85))))
        assertEquals(TempoRung.P100, view.nextRung)
        val waitOnly = piece("ode-to-joy", list(history = mapOf(played("ode-to-joy", cleanWait = true))))
        assertEquals(TempoRung.P60, waitOnly.nextRung)
    }

    @Test
    fun playedWithNothingCleanIsInProgressAtWait() {
        val view = piece("ode-to-joy", list(history = mapOf(played("ode-to-joy"))))
        assertEquals(PieceStatus.IN_PROGRESS, view.status)
        assertEquals(TempoRung.WAIT, view.nextRung)
    }

    @Test
    fun groupedByUnitInCurriculumOrder() {
        val views = list()
        val units = views.map { it.unitTitle }.distinct()
        assertEquals(listOf("Five-finger tunes", "Both hands", "Songs and chords"), units)
        assertEquals(views.map { it.unitTitle }, views.map { it.unitTitle }.sortedBy { units.indexOf(it) })
        val chordTitles = views.filter { it.unitTitle == "Songs and chords" }.map { it.passage.title }
        assertEquals(
            listOf(
                "Lightly Row with chords",
                "Ode to Joy with chords",
                "When the Saints Go Marching In",
                "Four-chord walk",
            ),
            chordTitles,
        )
        assertEquals(views.size, views.map { it.passage.id }.toSet().size)
    }
}
