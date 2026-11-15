package pianoanchor.feature.ear

import pianoanchor.core.model.EarChoice

/** What the user can do on the [EarScreen]; the composition root wires these to the ViewModel and navigation. */
public data class EarActions(
    val onBack: () -> Unit = {},
    val onReplay: () -> Unit = {},
    val onAnswer: (EarChoice) -> Unit = {},
    val onShowMe: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onDone: () -> Unit = {},
)
