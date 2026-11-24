package pianoanchor.feature.onboarding

/** What the user can do on the [OnboardingScreen]; the composition root wires these to the ViewModel and navigation. */
public data class OnboardingActions(
    val onOpenPianoList: () -> Unit = {},
    val onReplay: () -> Unit = {},
    val onSkipEcho: () -> Unit = {},
    val onContinue: () -> Unit = {},
    val onTestMe: () -> Unit = {},
    val onStartAtBeginning: () -> Unit = {},
    val onStopHere: () -> Unit = {},
    val onNextPiece: () -> Unit = {},
)
