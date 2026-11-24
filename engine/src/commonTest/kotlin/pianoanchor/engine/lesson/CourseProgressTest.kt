package pianoanchor.engine.lesson

import pianoanchor.core.model.Track
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.session.PassageHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CourseProgressTest {
    private val now = Instant.fromEpochSeconds(1)

    private fun waitClean(passageId: String) = passageId to PassageHistory(now, emptySet(), cleanWait = true)

    private val firstPassage = Curriculum.passages.first { Skills.MIDDLE_C.id in it.skills }

    @Test
    fun theCourseIsTheTwentyThreeCoreSkillsInMapOrder() {
        val course = CourseProgress.skills(Curriculum)
        assertEquals(23, course.size)
        assertTrue(course.all { it.track == Track.CORE })
        assertEquals(Skills.HAND_SHAPE, course.first())
        assertEquals(Skills.SHARPS_AND_FLATS, course.last())
    }

    @Test
    fun everyCourseSkillHasAPassageForTheUseStep() {
        assertTrue(CourseProgress.skills(Curriculum).all { skill -> Curriculum.passages.any { skill.id in it.skills } })
    }

    @Test
    fun placedSkillsAreTicked() {
        val unit0 =
            Curriculum.units
                .first()
                .skills
                .toSet()
        val ticked = CourseProgress.ticked(Curriculum, emptyMap(), placed = unit0 + "ear-skill-not-in-course")
        assertEquals(
            CourseProgress
                .skills(Curriculum)
                .map { it.id }
                .filter { it in unit0 }
                .toSet(),
            ticked,
        )
        assertEquals(Skills.RH_C_POSITION.id, CourseProgress.next(Curriculum, ticked)?.id)
    }

    @Test
    fun defaultPlacedIsEmpty() =
        assertEquals(
            CourseProgress.ticked(Curriculum, emptyMap(), emptySet()),
            CourseProgress.ticked(Curriculum, emptyMap()),
        )

    @Test
    fun noneCleanGivesHandShapeNext() {
        val ticked = CourseProgress.ticked(Curriculum, emptyMap())
        assertEquals(emptySet(), ticked)
        assertEquals(Skills.HAND_SHAPE, CourseProgress.next(Curriculum, ticked))
    }

    @Test
    fun middleCCleanGivesHandShapeNextBecauseMapOrderDoesNotSkip() {
        val ticked = CourseProgress.ticked(Curriculum, mapOf(waitClean(firstPassage.id)))
        assertTrue(Skills.MIDDLE_C.id in ticked)
        assertEquals(Skills.HAND_SHAPE, CourseProgress.next(Curriculum, ticked))
    }

    @Test
    fun handShapeAndMiddleCCleanGiveBlackKeysNext() {
        val handShape = Curriculum.passages.first { Skills.HAND_SHAPE.id in it.skills }
        val ticked = CourseProgress.ticked(Curriculum, mapOf(waitClean(handShape.id), waitClean(firstPassage.id)))
        assertEquals(Skills.BLACK_KEY_GROUPS, CourseProgress.next(Curriculum, ticked))
    }

    @Test
    fun everyPassageCleanGivesNull() {
        val all = Curriculum.passages.associate { waitClean(it.id) }
        val ticked = CourseProgress.ticked(Curriculum, all)
        assertEquals(23, ticked.size)
        assertNull(CourseProgress.next(Curriculum, ticked))
    }

    @Test
    fun aTimedOnlyCleanRunDoesNotTick() {
        val timed = mapOf(firstPassage.id to PassageHistory(now, setOf(60), cleanWait = false))
        assertEquals(emptySet(), CourseProgress.ticked(Curriculum, timed))
    }

    @Test
    fun theMoreSongsSkillsAreNeverTickedOrSuggested() {
        val all = Curriculum.passages.associate { waitClean(it.id) }
        assertTrue(CourseProgress.ticked(Curriculum, all).none { it.startsWith("more-") })
    }
}
