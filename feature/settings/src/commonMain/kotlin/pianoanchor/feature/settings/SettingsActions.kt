package pianoanchor.feature.settings

import pianoanchor.core.model.DailyBudget

/** What the user can do on the [SettingsScreen]; the composition root wires these to the ViewModel and navigation. */
public data class SettingsActions(
    val onBack: () -> Unit = {},
    val onDailyBudget: (DailyBudget) -> Unit = {},
    /** Receives -1 (fewer days) or +1 (more days). */
    val onWeeklyGoalStep: (Int) -> Unit = {},
    val onClickOn: (Boolean) -> Unit = {},
    val onUpdateCheckOn: (Boolean) -> Unit = {},
    val onDismissError: () -> Unit = {},
)
