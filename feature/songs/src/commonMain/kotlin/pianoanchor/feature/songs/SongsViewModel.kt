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
import pianoanchor.core.ports.LearnerRepositoryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.content.PieceStatus
import pianoanchor.engine.content.PieceView
import pianoanchor.engine.content.Pieces
import pianoanchor.engine.matcher.TempoRung
import pianoanchor.engine.session.passageHistories

/**
 * Songs: lists every piece of the curriculum with its status and tempo-ladder progress, from the skill states in
 * [learner] and each piece's history in [attempts], and follows the connected piano (the first of [input]'s
 * devices). A store that can't be read leaves [SongsState.storageFailed] until [refresh].
 */
public class SongsViewModel(
    private val learner: LearnerRepositoryPort,
    private val attempts: AttemptRepositoryPort,
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
     * Where tapping [piece] leads: nowhere (null) while it is locked, else Practice at its next rung (the top rung
     * once mastered), or Pianos when no piano is connected.
     */
    public fun target(piece: PieceView): SongsTarget? {
        val device = mutableState.value.piano
        return when {
            piece.status == PieceStatus.LOCKED -> null
            device == null -> SongsTarget.Pianos
            else -> SongsTarget.Practice(device, piece.passage.id, piece.nextRung ?: TempoRung.P100)
        }
    }

    private suspend fun loadPieces() {
        try {
            val states = learner.states()
            val history =
                attempts.passageHistories(Curriculum.units.flatMap { it.passages }.filter { it.piece })
            val pieces = Pieces.list(Curriculum, Curriculum.graph, states, history)
            mutableState.update { it.copy(pieces = pieces, storageFailed = false) }
        } catch (_: StorageUnavailableException) {
            mutableState.update { it.copy(pieces = null, storageFailed = true) }
        }
    }
}
