package pianoanchor.engine.content

import pianoanchor.core.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EarSkillsTest {
    @Test
    fun `eight ear skills are in the graph in teaching order`() {
        assertEquals(8, EarSkills.all.size)
        assertTrue(EarSkills.all.all { it.track == Track.EAR && Curriculum.graph.byId(it.id) == it })
    }

    @Test
    fun `each skill from the second on needs the one above it`() =
        EarSkills.all.zipWithNext().forEach { (above, skill) -> assertTrue(above.id in skill.prerequisites, skill.id) }

    @Test
    fun `the echoes also need a playing skill`() {
        assertEquals(setOf("kb-middle-c"), EarSkills.ECHO_NOTE.prerequisites)
        assertEquals(setOf("ear-step-skip", "rh-c-position"), EarSkills.ECHO_THREE.prerequisites)
        assertEquals(setOf("ear-chord-quality", "rh-skips"), EarSkills.ECHO_FIVE.prerequisites)
    }
}
