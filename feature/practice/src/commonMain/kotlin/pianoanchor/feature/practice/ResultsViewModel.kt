package pianoanchor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.engine.content.Passages

/**
 * The results of the run saved as [attemptId] in [attempts]: [ResultsState.Loading] until it is read, then
 * [ResultsState.Ready] or [ResultsState.NotFound], or [ResultsState.Unavailable] when the store can't be read. The
 * run number is one more than the passage's runs started before this one.
 */
public class ResultsViewModel(
    private val attemptId: String,
    private val attempts: AttemptRepositoryPort,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ResultsState>(ResultsState.Loading)
    public val state: StateFlow<ResultsState> = mutableState.asStateFlow()

    init {
        retry()
    }

    /** Reads the run again ([ResultsState.Loading] meanwhile), e.g. after [ResultsState.Unavailable]. */
    public fun retry() {
        mutableState.value = ResultsState.Loading
        viewModelScope.launch {
            mutableState.value =
                try {
                    load()
                } catch (_: StorageUnavailableException) {
                    ResultsState.Unavailable
                }
        }
    }

    private suspend fun load(): ResultsState {
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
