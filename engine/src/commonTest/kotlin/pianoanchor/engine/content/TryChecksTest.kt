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
        assertEquals(23, courseIds.size)
        courseIds.forEach { assertNotNull(Curriculum.tryCheck(it), it) }
        assertEquals(courseIds.toSet(), TryChecks.all.keys)
    }

    @Test
    fun `every passage check names a passage of its skill`() {
        val passageChecks = courseIds.filter { Curriculum.tryCheck(it) is TryCheck.Passage }
        assertEquals(6, passageChecks.size)
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
    fun `the high and low check finds eight cs on 88 keys`() {
        assertEquals(TryCheck.FindAll(0, 21..108), Curriculum.tryCheck("high-and-low"))
    }
}
