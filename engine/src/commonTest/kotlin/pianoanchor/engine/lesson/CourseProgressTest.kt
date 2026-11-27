package pianoanchor.engine.lesson

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Rating
import pianoanchor.core.model.Review
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

    private fun waitClean(passageId: String) =
        passageId to PassageHistory(now, emptySet(), cleanWait = true, firstCleanUnaidedWait = now)

    private val firstPassage = Curriculum.passages.first { Skills.MIDDLE_C.id in it.skills }

    @Test
    fun theCourseIsTheTwentySevenCoreSkillsInMapOrder() {
        val course = CourseProgress.skills(Curriculum)
        assertEquals(27, course.size)
        assertTrue(course.all { it.track == Track.CORE })
        assertEquals(Skills.HAND_SHAPE, course.first())
        assertEquals(Skills.RHYTHM_SONG, course.last())
    }

    @Test
    fun everyCourseSkillHasAPassageForTheUseStep() {
        assertTrue(CourseProgress.skills(Curriculum).all { skill -> Curriculum.passages.any { skill.id in it.skills } })
    }

    @Test
    fun placedSkillsArePassed() {
        val unit0 =
            Curriculum.units
                .first()
                .skills
                .toSet()
        val passed = CourseProgress.passed(Curriculum, emptyMap(), placed = unit0 + "ear-skill-not-in-course")
        assertEquals(
            CourseProgress
                .skills(Curriculum)
                .map { it.id }
                .filter { it in unit0 }
                .toSet(),
            passed,
        )
        assertEquals(Skills.RH_C_POSITION.id, CourseProgress.next(Curriculum, passed)?.id)
    }

    @Test
    fun defaultPlacedIsEmpty() =
        assertEquals(
            CourseProgress.passed(Curriculum, emptyMap(), emptySet()),
            CourseProgress.passed(Curriculum, emptyMap()),
        )

    @Test
    fun noneCleanGivesHandShapeNext() {
        val passed = CourseProgress.passed(Curriculum, emptyMap())
        assertEquals(emptySet(), passed)
        assertEquals(Skills.HAND_SHAPE, CourseProgress.next(Curriculum, passed))
    }

    @Test
    fun middleCCleanGivesHandShapeNextBecauseMapOrderDoesNotSkip() {
        val passed = CourseProgress.passed(Curriculum, mapOf(waitClean(firstPassage.id)))
        assertTrue(Skills.MIDDLE_C.id in passed)
        assertEquals(Skills.HAND_SHAPE, CourseProgress.next(Curriculum, passed))
    }

    @Test
    fun handShapeAndMiddleCCleanGiveBlackKeysNext() {
        val handShape = Curriculum.passages.first { Skills.HAND_SHAPE.id in it.skills }
        val passed = CourseProgress.passed(Curriculum, mapOf(waitClean(handShape.id), waitClean(firstPassage.id)))
        assertEquals(Skills.BLACK_KEY_GROUPS, CourseProgress.next(Curriculum, passed))
    }

    @Test
    fun everyPassageCleanGivesNull() {
        val all = Curriculum.passages.associate { waitClean(it.id) }
        val passed = CourseProgress.passed(Curriculum, all)
        assertEquals(27, passed.size)
        assertNull(CourseProgress.next(Curriculum, passed))
    }

    @Test
    fun aTimedOnlyCleanRunDoesNotPass() {
        val timed = mapOf(firstPassage.id to PassageHistory(now, setOf(60), cleanWait = false))
        assertEquals(emptySet(), CourseProgress.passed(Curriculum, timed))
    }

    @Test
    fun aCleanWaitRunWithAHintDoesNotPass() {
        val aided = mapOf(firstPassage.id to PassageHistory(now, emptySet(), cleanWait = true))
        assertEquals(emptySet(), CourseProgress.passed(Curriculum, aided))
    }

    private val day1 = LocalDate(2026, 11, 20)
    private val day2 = LocalDate(2026, 11, 21)
    private val middleC = Skills.MIDDLE_C.id

    private fun quiz(
        day: LocalDate,
        rating: Rating = Rating.GOOD,
        unaided: Boolean = true,
        skill: String = middleC,
    ) = Review(skill, day, rating, "quiz:1", unaided)

    @Test
    fun passedOnIsTheLearnerDayOfTheEarliestUnaidedCleanRun() {
        val zone = TimeZone.UTC
        val early = Instant.parse("2026-11-20T10:00:00Z")
        val late = Instant.parse("2026-11-22T10:00:00Z")
        val nightOwl = Instant.parse("2026-11-21T01:00:00Z")
        val history =
            mapOf(
                firstPassage.id to PassageHistory(late, emptySet(), true, late),
                Curriculum.passages.first { Skills.HAND_SHAPE.id in it.skills }.id to
                    PassageHistory(nightOwl, emptySet(), true, nightOwl),
            )
        val days = CourseProgress.passedOn(Curriculum, history, zone)
        // 01:00 is still the learner day before.
        assertEquals(day1, days[Skills.HAND_SHAPE.id])
        assertEquals(LocalDate(2026, 11, 22), days[middleC])
        val both = history + (firstPassage.id to PassageHistory(early, emptySet(), true, early))
        assertEquals(day1, CourseProgress.passedOn(Curriculum, both, zone)[middleC])
        val aided = mapOf(firstPassage.id to PassageHistory(now, emptySet(), true))
        assertEquals(emptyMap(), CourseProgress.passedOn(Curriculum, aided, zone))
    }

    @Test
    fun aGoodUnaidedQuizOnThePassDayDoesNotMakeItLearned() {
        assertEquals(emptySet(), CourseProgress.learned(Curriculum, mapOf(middleC to day1), listOf(quiz(day1))))
    }

    @Test
    fun aGoodUnaidedQuizOnALaterDayMakesItLearned() {
        assertEquals(setOf(middleC), CourseProgress.learned(Curriculum, mapOf(middleC to day1), listOf(quiz(day2))))
        assertEquals(
            setOf(middleC),
            CourseProgress.learned(Curriculum, mapOf(middleC to day1), listOf(quiz(day2, Rating.HARD))),
        )
    }

    @Test
    fun anAgainOrAidedQuizDoesNotMakeItLearned() {
        val passed = mapOf(middleC to day1)
        assertEquals(emptySet(), CourseProgress.learned(Curriculum, passed, listOf(quiz(day2, Rating.AGAIN))))
        assertEquals(emptySet(), CourseProgress.learned(Curriculum, passed, listOf(quiz(day2, unaided = false))))
        assertEquals(
            emptySet(),
            CourseProgress.learned(Curriculum, passed, listOf(quiz(day2, skill = Skills.HAND_SHAPE.id))),
        )
    }

    @Test
    fun placedSkillsAreLearnedAndAnUnpassedSkillIsNot() {
        assertEquals(
            setOf(middleC),
            CourseProgress.learned(Curriculum, emptyMap(), emptyList(), setOf(middleC, "ear-x")),
        )
        assertEquals(emptySet(), CourseProgress.learned(Curriculum, emptyMap(), listOf(quiz(day2))))
    }

    @Test
    fun theMoreSongsSkillsAreNeverPassedOrSuggested() {
        val all = Curriculum.passages.associate { waitClean(it.id) }
        assertTrue(CourseProgress.passed(Curriculum, all).none { it.startsWith("more-") })
    }
}
