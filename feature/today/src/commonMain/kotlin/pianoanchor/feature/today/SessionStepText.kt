package pianoanchor.feature.today

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.SessionStep
import pianoanchor.feature.today.resources.Res
import pianoanchor.feature.today.resources.session_step_ear
import pianoanchor.feature.today.resources.session_step_learn
import pianoanchor.feature.today.resources.session_step_play_along
import pianoanchor.feature.today.resources.session_step_practise
import pianoanchor.feature.today.resources.session_step_sight
import pianoanchor.feature.today.resources.session_step_warm_up_cold
import pianoanchor.feature.today.resources.session_step_warm_up_quizzes

/** The title of [step]: its kind and what it is about ("Learn: Middle C"). */
@Composable
internal fun stepTitle(step: SessionStep): String =
    when (val card = step.card) {
        is Card.ColdOpen -> {
            stringResource(Res.string.session_step_warm_up_cold, card.skill.title)
        }

        is Card.Quizzes -> {
            stringResource(Res.string.session_step_warm_up_quizzes)
        }

        is Card.Lesson -> {
            stringResource(Res.string.session_step_learn, card.skill.title)
        }

        is Card.Song -> {
            val along = step is SessionStep.PlayAlong
            val kind = if (along) Res.string.session_step_play_along else Res.string.session_step_practise
            stringResource(kind, card.passage.title)
        }

        is Card.Ear -> {
            stringResource(Res.string.session_step_ear)
        }

        is Card.SightReading -> {
            stringResource(Res.string.session_step_sight)
        }
    }
