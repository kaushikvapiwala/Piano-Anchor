package pianoanchor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.content.Passages

/**
 * The results of the run saved as [attemptId] in [attempts]: [ResultsState.Loading] until it is read, then
 * [ResultsState.Ready] or [ResultsState.NotFound]. The run number is one more than the passage's runs started
 * before this one.
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
        return ResultsState.Ready(
            passageTitle = Passages.byId(attempt.passageId)?.title ?: attempt.passageId,
            runNumber = attempts.countStartedBefore(attempt.passageId, attempt.startedAt) + 1,
            band = attempt.band,
            // Wait mode moves on only when a step is right, so a finished run has every step correct.
            correctSteps = attempt.stepCount,
            stepCount = attempt.stepCount,
            wrongNotes = attempt.wrongNotes,
            incompleteChords = attempt.incompleteChords,
        )
    }
}
