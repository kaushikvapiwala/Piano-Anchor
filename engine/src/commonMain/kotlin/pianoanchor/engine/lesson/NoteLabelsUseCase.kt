package pianoanchor.engine.lesson

import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.NoteNames
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.session.passageHistories

/**
 * The [NoteLabels] a key strip draws for the learner: the `Settings.noteNames` choice with the course concepts passed
 * and learned ([CourseProgress]), read from [settings], [attempts] and [learner]. Shared by every screen with a strip.
 */
public class NoteLabelsUseCase(
    private val settings: SettingsRepositoryPort,
    private val attempts: AttemptRepositoryPort,
    private val learner: LearnerModel,
    private val zone: TimeZone,
) {
    /** The labels now; when storage can't be read, the beginner's default (every white key lettered). */
    public suspend fun load(): NoteLabels =
        try {
            val setting = settings.settings.first().noteNames
            if (setting == NoteNames.OFF) return NoteLabels.of(setting, emptySet(), emptySet())
            val history = attempts.passageHistories(Curriculum.passages)
            val placed = learner.placedSkills()
            val passed = CourseProgress.passed(Curriculum, history, placed)
            val passedOn = CourseProgress.passedOn(Curriculum, history, zone)
            val learned = CourseProgress.learned(Curriculum, passedOn, learner.reviewsOf(passedOn.keys), placed)
            NoteLabels.of(setting, passed, learned)
        } catch (_: StorageUnavailableException) {
            NoteLabels.of(NoteNames.AUTO, emptySet(), emptySet())
        }
}
