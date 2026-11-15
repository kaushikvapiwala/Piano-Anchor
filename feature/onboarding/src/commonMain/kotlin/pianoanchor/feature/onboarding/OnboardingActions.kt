package pianoanchor.feature.onboarding

/** What the user can do on the [OnboardingScreen]; the composition root wires these to the ViewModel and navigation. */
public data class OnboardingActions(
    val onOpenPianoList: () -> Unit = {},
    val onReplay: () -> Unit = {},
    val onSkipEcho: () -> Unit = {},
    val onContinue: () -> Unit = {},
    val onStart: (StartPoint) -> Unit = {},
)
