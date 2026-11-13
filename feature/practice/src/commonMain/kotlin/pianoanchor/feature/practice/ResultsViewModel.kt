package pianoanchor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Passages

/**
 * The results of the run saved as [attemptId] in [attempts]: [ResultsState.Loading] until it is read, then
 * [ResultsState.Ready] or [ResultsState.NotFound]. The run number counts the passage's older runs among its newest
 * `Tunables.RESULTS_HISTORY_LIMIT`.
 */
public class ResultsViewModel(
    attemptId: String,
    private val attempts: AttemptRepositoryPort,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ResultsState>(ResultsState.Loading)
    public val state: StateFlow<ResultsState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { mutableState.value = load(attemptId) }
    }

    private suspend fun load(attemptId: String): ResultsState {
        val attempt = attempts.get(attemptId) ?: return ResultsState.NotFound
        val history = attempts.recent(attempt.passageId, Tunables.RESULTS_HISTORY_LIMIT).first()
        return ResultsState.Ready(
            passageTitle = Passages.byId(attempt.passageId)?.title ?: attempt.passageId,
            runNumber = history.count { it.startedAt < attempt.startedAt } + 1,
            band = attempt.band,
            // Wait mode moves on only when a step is right, so a finished run has every step correct.
            correctSteps = attempt.stepCount,
            stepCount = attempt.stepCount,
            wrongNotes = attempt.wrongNotes,
            incompleteChords = attempt.incompleteChords,
        )
    }
}
