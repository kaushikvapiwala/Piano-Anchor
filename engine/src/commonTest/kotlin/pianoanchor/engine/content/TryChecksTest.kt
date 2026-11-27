package pianoanchor.engine.content

import pianoanchor.core.model.Track
import pianoanchor.core.model.TryCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TryChecksTest {
    private val courseIds =
        Curriculum.graph.skills
            .filter { it.track == Track.CORE }
            .map { it.id }

    @Test
    fun `every course skill has a check`() {
        assertEquals(32, courseIds.size)
        courseIds.forEach { assertNotNull(Curriculum.tryCheck(it), it) }
        assertEquals(courseIds.toSet(), TryChecks.all.keys)
    }

    @Test
    fun `every passage check names a passage of its skill`() {
        val passageChecks = courseIds.filter { Curriculum.tryCheck(it) is TryCheck.Passage }
        assertEquals(8, passageChecks.size)
        passageChecks.forEach { id ->
            val check = Curriculum.tryCheck(id) as TryCheck.Passage
            val passage = Curriculum.passage(check.passageId)
            assertNotNull(passage, id)
            assertTrue(id in passage.skills, id)
        }
    }

    @Test
    fun `skills without a check are the more songs and ear skills`() {
        Curriculum.graph.skills.filter { it.track != Track.CORE }.forEach {
            assertNull(
                Curriculum.tryCheck(it.id),
                it.id,
            )
        }
        assertNull(Curriculum.tryCheck("no-such-skill"))
    }

    @Test
    fun `the unit 4 checks are the rhythms of the spec and Jingle Bells in Use`() {
        assertEquals(TryCheck.Rhythm(60, List(8) { 480 }, 60), Curriculum.tryCheck("eighth-notes"))
        assertEquals(TryCheck.Rhythm(60, listOf(960, 960, 480, 480, 960), 60), Curriculum.tryCheck("eighth-pairs"))
        assertEquals(TryCheck.Rhythm(60, listOf(1440, 480, 1920), 60), Curriculum.tryCheck("dotted-quarter"))
        assertEquals(TryCheck.Passage("builtin:jingle-bells"), Curriculum.tryCheck("rhythm-song"))
    }

    @Test
    fun `the unit 5 checks are the five rows of the spec`() {
        assertEquals(
            TryCheck.Sequence(listOf(67, 69, 71, 72, 74, 72, 71, 69, 67)),
            Curriculum.tryCheck("rh-g-position"),
        )
        assertEquals(TryCheck.Sequence(listOf(50, 48, 47, 45, 43)), Curriculum.tryCheck("lh-g-position"))
        assertEquals(TryCheck.FindAll(6, 21..108), Curriculum.tryCheck("key-of-g"))
        assertEquals(TryCheck.Chord(setOf(42, 48, 50)), Curriculum.tryCheck("g-and-d7-chords"))
        assertEquals(TryCheck.Passage("builtin:go-tell-aunt-rhody"), Curriculum.tryCheck("melody-chords-in-g"))
    }

    @Test
    fun `the high and low check finds eight cs on 88 keys`() {
        assertEquals(TryCheck.FindAll(0, 21..108), Curriculum.tryCheck("high-and-low"))
    }
}
