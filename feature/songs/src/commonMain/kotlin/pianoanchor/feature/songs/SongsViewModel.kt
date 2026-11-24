package pianoanchor.feature.songs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.PieceView
import pianoanchor.engine.content.Pieces
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.lesson.CourseProgress
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.passageHistories

/**
 * Songs: lists every piece of the curriculum with its status and tempo-ladder progress, from each piece's history in
 * [attempts], with the course ticks (from [attempts] and the placed skills of [learner]) and the suggested next concept
 * (`CourseProgress`), and follows the connected piano (the first of [input]'s devices). Every piece is open. A store
 * that can't be read leaves [SongsState.storageFailed] until [refresh].
 */
public class SongsViewModel(
    private val attempts: AttemptRepositoryPort,
    private val learner: LearnerModel,
    input: MidiInputPort,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SongsState())
    public val state: StateFlow<SongsState> = mutableState.asStateFlow()

    private var load: Job? = null

    init {
        viewModelScope.launch {
            input.inputDevices.collect { devices ->
                mutableState.update { it.copy(piano = devices.firstOrNull()) }
            }
        }
        refresh()
    }

    /** Loads the pieces again, e.g. after coming back from a run or after a failed read. */
    public fun refresh() {
        load?.cancel()
        load = viewModelScope.launch { loadPieces() }
    }

    /**
     * Where tapping [piece] leads: Practice at its next rung (the top rung once mastered), or Pianos when no piano is
     * connected.
     */
    public fun target(piece: PieceView): SongsTarget {
        val device = mutableState.value.piano ?: return SongsTarget.Pianos
        return SongsTarget.Practice(device, piece.passage.id, piece.nextRung ?: TempoRung.P100)
    }

    private suspend fun loadPieces() {
        try {
            // Every passage: a concept ticks on a clean wait-mode run of any of its passages, pieces or not.
            val history = attempts.passageHistories(Curriculum.passages)
            val pieces = Pieces.list(Curriculum, history)
            val ticked = CourseProgress.ticked(Curriculum, history, learner.placedSkills())
            val next = CourseProgress.next(Curriculum, ticked)?.id
            mutableState.update { it.copy(pieces = pieces, ticked = ticked, next = next, storageFailed = false) }
        } catch (_: StorageUnavailableException) {
            mutableState.update { it.copy(pieces = null, ticked = emptySet(), next = null, storageFailed = true) }
        }
    }
}
