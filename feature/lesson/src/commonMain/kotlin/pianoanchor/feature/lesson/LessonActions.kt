package pianoanchor.feature.lesson

import pianoanchor.core.model.LessonStep

/** What the user can do on the [LessonScreen]; the composition root wires these to the ViewModel and navigation. */
public data class LessonActions(
    val onBack: () -> Unit = {},
    val onGoTo: (LessonStep) -> Unit = {},
    val onNext: () -> Unit = {},
    val onSkip: () -> Unit = {},
    val onPlayAgain: () -> Unit = {},
    val onPlayIt: () -> Unit = {},
    val onNextLesson: () -> Unit = {},
    val onDone: () -> Unit = {},
)
