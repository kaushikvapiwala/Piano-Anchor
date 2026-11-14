package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillGraphTest {
    private fun skill(
        id: String,
        vararg prerequisites: String,
    ) = Skill(id, id.uppercase(), prerequisites.toSet())

    private fun ids(skills: List<Skill>) = skills.map { it.id }

    @Test
    fun ordersPrerequisitesFirst() {
        val graph = SkillGraph(listOf(skill("c", "b"), skill("b", "a"), skill("a")))
        assertEquals(listOf("a", "b", "c"), ids(graph.skills))
    }

    @Test
    fun tiesOrderById() {
        val graph = SkillGraph(listOf(skill("z"), skill("m", "z"), skill("b"), skill("y", "b")))
        assertEquals(listOf("b", "y", "z", "m"), ids(graph.skills))
    }

    @Test
    fun byIdFindsASkillOrNull() {
        val graph = SkillGraph(listOf(skill("a")))
        assertEquals(skill("a"), graph.byId("a"))
        assertNull(graph.byId("b"))
    }

    @Test
    fun rejectsDuplicateId() {
        val e = assertFailsWith<IllegalArgumentException> { SkillGraph(listOf(skill("a"), skill("a"))) }
        assertTrue("a" in e.message.orEmpty())
    }

    @Test
    fun rejectsUnknownPrerequisite() {
        val e = assertFailsWith<IllegalArgumentException> { SkillGraph(listOf(skill("a", "ghost"))) }
        assertTrue("a" in e.message.orEmpty() && "ghost" in e.message.orEmpty())
    }

    @Test
    fun rejectsSelfPrerequisite() {
        val e = assertFailsWith<IllegalArgumentException> { SkillGraph(listOf(skill("a", "a"))) }
        assertTrue("a" in e.message.orEmpty())
    }

    @Test
    fun rejectsACycleAndNamesIt() {
        val e =
            assertFailsWith<IllegalArgumentException> {
                SkillGraph(listOf(skill("start"), skill("a", "c", "start"), skill("b", "a"), skill("c", "b")))
            }
        assertTrue("a -> c -> b -> a" in e.message.orEmpty(), e.message)
    }

    @Test
    fun rejectsANonKebabId() {
        listOf("", " ", "Upper", "snake_case", "-lead", "trail-", "double--dash", "sp ace").forEach { id ->
            assertFailsWith<IllegalArgumentException>(id) { SkillGraph(listOf(skill(id))) }
        }
        SkillGraph(listOf(skill("rh-c-position"), skill("a1-b2")))
    }

    @Test
    fun unlockedListsSkillsWhosePrerequisitesAreLearned() {
        val graph = SkillGraph(listOf(skill("a"), skill("b", "a"), skill("c", "a", "b"), skill("d")))
        assertEquals(listOf("b", "d"), ids(graph.unlocked(setOf("a"))))
        assertEquals(listOf("c", "d"), ids(graph.unlocked(setOf("a", "b"))))
    }

    @Test
    fun unlockedExcludesLearnedSkills() {
        val graph = SkillGraph(listOf(skill("a"), skill("b", "a")))
        assertEquals(listOf("b"), ids(graph.unlocked(setOf("a"))))
        assertEquals(emptyList(), ids(graph.unlocked(setOf("a", "b"))))
    }

    @Test
    fun aSkillWithoutPrerequisitesIsUnlockedFromTheStart() {
        val graph = SkillGraph(listOf(skill("b", "a"), skill("a")))
        assertEquals(listOf("a"), ids(graph.unlocked(emptySet())))
    }
}
