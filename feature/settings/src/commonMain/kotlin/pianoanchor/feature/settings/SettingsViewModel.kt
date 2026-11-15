package pianoanchor.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pianoanchor.core.model.DailyBudget
import pianoanchor.core.model.Settings
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.WEEKLY_GOAL_DAYS_RANGE
import pianoanchor.core.ports.SettingsRepositoryPort

/**
 * The settings screen over [repository]. Each control stores its change at once and the screen shows what the store
 * holds, so a change that couldn't be stored snaps back and raises [SettingsState.storageFailed].
 */
public class SettingsViewModel(
    private val repository: SettingsRepositoryPort,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsState())
    public val state: StateFlow<SettingsState> = mutableState.asStateFlow()

    private var collector: Job = follow()

    /** Shows what the store holds from now on; ends, with the error card, if the store can't be read. */
    private fun follow(): Job =
        viewModelScope.launch {
            try {
                repository.settings.collect { stored -> mutableState.update { it.copy(settings = stored) } }
            } catch (_: StorageUnavailableException) {
                mutableState.update { it.copy(storageFailed = true) }
            }
        }

    /** Sets the practice time per day. */
    public fun setDailyBudget(budget: DailyBudget): Unit = change { it.copy(dailyBudget = budget) }

    /** Moves the practice days per week by [by], kept within [WEEKLY_GOAL_DAYS_RANGE]. */
    public fun changeWeeklyGoalDays(by: Int): Unit =
        change { it.copy(weeklyGoalDays = (it.weeklyGoalDays + by).coerceIn(WEEKLY_GOAL_DAYS_RANGE)) }

    /** Turns the metronome click of timed runs on or off. */
    public fun setClickOn(on: Boolean): Unit = change { it.copy(clickOn = on) }

    /** Turns the daily check for a new version on or off. */
    public fun setUpdateCheckOn(on: Boolean): Unit = change { it.copy(updateCheckOn = on) }

    /** Hides the error card. */
    public fun dismissError() {
        mutableState.update { it.copy(storageFailed = false) }
    }

    private fun change(transform: (Settings) -> Settings) {
        viewModelScope.launch {
            try {
                repository.update(transform)
                mutableState.update { it.copy(storageFailed = false) }
                // A failed read ended the collector: the store works again, so follow it again.
                if (!collector.isActive) collector = follow()
            } catch (_: StorageUnavailableException) {
                mutableState.update { it.copy(storageFailed = true) }
            }
        }
    }
}
