package pianoanchor.engine.lesson

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.NoteNames
import pianoanchor.core.model.Settings
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.Skills
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.testing.FakeAttemptRepository
import pianoanchor.testing.FakeLearnerRepository
import pianoanchor.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteLabelsUseCaseTest {
    private val settings = FakeSettingsRepository()
    private val attempts = FakeAttemptRepository()
    private val model = LearnerModel(Curriculum.graph, FakeLearnerRepository())
    private val useCase = NoteLabelsUseCase(settings, attempts, model, TimeZone.UTC)

    @Test
    fun `a new learner sees every white key lettered`() =
        runTest {
            assertEquals(NoteLabels(NoteNameStage.ALL_WHITE, sharpsPassed = false, hints = true), useCase.load())
        }

    @Test
    fun `placed key names are passed and learned so the letters fade`() =
        runTest {
            model.place(setOf(Skills.WHITE_KEY_NAMES.id), LocalDate(2026, 11, 1))

            assertEquals(NoteLabels(NoteNameStage.NONE, sharpsPassed = false, hints = true), useCase.load())
        }

    @Test
    fun `off needs no progress`() =
        runTest {
            settings.update { Settings(noteNames = NoteNames.OFF) }

            assertEquals(NoteLabels(NoteNameStage.NONE, sharpsPassed = false, hints = false), useCase.load())
        }

    @Test
    fun `unreadable storage falls back to the beginner default`() =
        runTest {
            settings.failing = true

            assertEquals(NoteNameStage.ALL_WHITE, useCase.load().stage)
        }
}
