package pianoanchor.feature.ear

import pianoanchor.core.model.EarChoice
import pianoanchor.core.model.EarExercise
import pianoanchor.core.model.MidiNote

private fun notes(vararg numbers: Int) = numbers.map { setOf(MidiNote(it)) }

internal val IDENTIFY: EarExercise.Identify =
    EarExercise.Identify(notes(60, 67), listOf(EarChoice.HIGHER, EarChoice.LOWER), EarChoice.HIGHER)

internal val ECHO: EarExercise.Echo = EarExercise.Echo(notes(60, 64, 62), exactOctave = false)

internal val IDENTIFY_STATE =
    EarState(
        skillTitle = "Higher or lower",
        skillNumber = 1,
        skillCount = 3,
        itemNumber = 2,
        exercise = IDENTIFY,
    )

internal val ECHO_STATE =
    EarState(
        skillTitle = "Echo three notes",
        skillNumber = 2,
        skillCount = 3,
        itemNumber = 1,
        exercise = ECHO,
    )
