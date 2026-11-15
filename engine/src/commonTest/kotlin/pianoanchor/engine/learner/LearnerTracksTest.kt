package pianoanchor.engine.learner

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Skill
import pianoanchor.core.model.SkillGraph
import pianoanchor.core.model.Track
import pianoanchor.engine.Tunables
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.passageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class LearnerTracksTest {
    private val repository = FakeLearnerRepository()
    private val start = LocalDate(2026, 11, 1)
    private var attempts = 0

    private fun day(offset: Int) = start.plus(offset, DateTimeUnit.DAY)

    private fun skill(
        id: String,
        track: Track,
        vararg prerequisites: String,
    ) = Skill(id, id, prerequisites.toSet(), track)

    private fun modelOf(vararg skills: Skill) = LearnerModel(SkillGraph(skills.toList()), repository)

    private suspend fun LearnerModel.check(
        skill: String,
        offset: Int,
        band: Band = Band.CLEAN,
    ) = record(
        passageOf(setOf(MidiNote(60)), skills = setOf(skill)),
        Attempt(
            id = "attempt-${++attempts}",
            passageId = "test:passage",
            deviceId = "usb-1",
            startedAt = Instant.parse("2026-11-01T10:00:00Z"),
            durationNanos = 1,
            stepCount = 1,
            wrongNotes = 0,
            incompleteChords = 0,
            accuracy = 1.0,
            band = band,
            events = emptyList(),
            tempoBpm = 60,
            playedNotes = emptyList(),
            hintShown = false,
            mode = RunMode.PERFORM,
        ),
        day(offset),
    )

    @Test
    fun `due list mixes tracks by retrievability`() =
        runTest {
            val model = modelOf(skill("c", Track.CORE), skill("s", Track.SONGS), skill("e", Track.EAR))
            model.check("e", 0)
            model.check("c", 1)
            model.check("s", 2)

            // Lowest retrievability first: the one reviewed longest ago, whatever its track.
            assertEquals(listOf("e", "c", "s"), model.plan(day(30)).due.map { it.skillId })
        }

    @Test
    fun `new skills rotate to the track introduced least recently`() =
        runTest {
            val model =
                modelOf(
                    skill("c1", Track.CORE),
                    skill("c2", Track.CORE),
                    skill("e1", Track.EAR),
                    skill("s1", Track.SONGS),
                )
            model.check("c1", 0)
            model.check("s1", 1)
            model.check("e1", 2)
            // Core was introduced longest ago, so it goes first; nothing else is unlocked.
            assertEquals(listOf("c2"), model.plan(day(2)).new.map { it.id })
        }

    @Test
    fun `a track never introduced goes first`() =
        runTest {
            val model =
                modelOf(
                    skill("c1", Track.CORE),
                    skill("c2", Track.CORE),
                    skill("c3", Track.CORE),
                    skill("e1", Track.EAR),
                )
            model.check("c1", 0)

            assertEquals(listOf("e1", "c2"), model.plan(day(1)).new.map { it.id })
        }

    @Test
    fun `ties follow track order`() =
        runTest {
            val model =
                modelOf(skill("e1", Track.EAR), skill("s1", Track.SONGS), skill("c1", Track.CORE))
            assertEquals(listOf("c1", "s1"), model.plan(day(0)).new.map { it.id })
            assertEquals(Tunables.MAX_NEW_PER_DAY, 2)
        }

    @Test
    fun `a track with nothing unlocked is skipped`() =
        runTest {
            val model =
                modelOf(
                    skill("c1", Track.CORE),
                    skill("c2", Track.CORE),
                    skill("e1", Track.EAR, "c1"),
                )
            // The ear skill waits for its core prerequisite: both slots go to core.
            assertEquals(listOf("c1", "c2"), model.plan(day(0)).new.map { it.id })
        }

    @Test
    fun `cross-track prerequisite gates a skill`() =
        runTest {
            val model = modelOf(skill("c1", Track.CORE), skill("e1", Track.EAR, "c1"))
            model.check("c1", 0)
            assertTrue("e1" !in model.plan(day(1)).new.map { it.id })
            model.check("c1", 3)
            assertTrue("e1" in model.plan(day(4)).new.map { it.id })
        }

    @Test
    fun `tracks share the daily plan`() =
        runTest {
            val skills =
                listOf(
                    skill("c1", Track.CORE),
                    skill("c2", Track.CORE, "c1"),
                    skill("c3", Track.CORE, "c2"),
                    skill("c4", Track.CORE, "c3"),
                    skill("s1", Track.SONGS),
                    skill("s2", Track.SONGS, "s1"),
                    skill("s3", Track.SONGS, "s2"),
                    skill("s4", Track.SONGS, "s3"),
                    skill("e1", Track.EAR, "c1"),
                    skill("e2", Track.EAR, "e1"),
                    skill("e3", Track.EAR, "e2"),
                    skill("e4", Track.EAR, "e3"),
                )
            val graph = SkillGraph(skills)
            val model = LearnerModel(graph, repository)
            val waiting = Track.entries.associateWith { 0 }.toMutableMap()
            for (offset in 0 until 30) {
                val plan = model.plan(day(offset))
                assertTrue(plan.due.size <= Tunables.MAX_DUE_PER_DAY)
                val learned =
                    repository
                        .states()
                        .values
                        .filter { it.learned }
                        .map { it.skillId }
                        .toSet()
                val ready =
                    graph
                        .unlocked(learned)
                        .filter { it.id !in repository.states() }
                        .map { it.track }
                        .toSet()
                val introduced = plan.new.map { it.track }.toSet()
                for (track in Track.entries) {
                    waiting[track] = if (track in ready && track !in introduced) waiting.getValue(track) + 1 else 0
                    assertTrue(waiting.getValue(track) <= 2, "$track waited ${waiting[track]} days at day $offset")
                }
                plan.due.forEach { model.check(it.skillId, offset) }
                plan.new.forEach { model.check(it.id, offset) }
            }
            assertEquals(skills.map { it.id }.toSet(), repository.states().keys)
        }
}
