package pianoanchor.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Passage
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.core.ports.TonePort
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.progress.TakeLane
import pianoanchor.engine.progress.TakeReplay
import pianoanchor.engine.session.WeekStatusUseCase
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock

private const val DAYS_PER_WEEK = 7

/**
 * Progress: this week's days goal, streak and pause status (counted as Today does, by [WeekStatusUseCase]), the
 * minutes played on each day of the week (Monday to Sunday), the skills learned since Monday and "Day 1 vs today": the
 * piece played most recently whose first and latest full runs started on different days in [zone], with a
 * note-timing lane per take. Days follow [clock] in [zone]. A store that can't be read leaves
 * [ProgressState.storageFailed] until [refresh].
 *
 * [play] sounds one take on [tone] at a time (starting one stops the one playing); [stop] and clearing the ViewModel
 * stop the sound.
 */
public class ProgressViewModel(
    private val attempts: AttemptRepositoryPort,
    private val learner: LearnerModel,
    private val settings: SettingsRepositoryPort,
    private val tone: TonePort,
    private val clock: Clock,
    private val zone: TimeZone,
) : ViewModel() {
    private val weekStatus = WeekStatusUseCase(attempts, learner, zone)
    private val mutableState = MutableStateFlow(ProgressState())
    public val state: StateFlow<ProgressState> = mutableState.asStateFlow()

    private var load: Job? = null
    private var playback: Job? = null

    init {
        refresh()
    }

    /** Loads the tab again, e.g. after a failed read. */
    public fun refresh() {
        load?.cancel()
        load = viewModelScope.launch { loadProgress() }
    }

    /** Plays [take] of the comparison, stopping the one that sounds; does nothing without a comparison. */
    public fun play(take: Take) {
        val compare = mutableState.value.compare ?: return
        val attempt = if (take == Take.DAY_1) compare.day1 else compare.today
        playback?.cancel()
        mutableState.update { it.copy(playing = take) }
        playback =
            viewModelScope.launch {
                val self = coroutineContext.job
                try {
                    tone.play(TakeReplay.tones(attempt))
                } finally {
                    // A take that was replaced by another leaves the new one's state alone.
                    if (playback === self) {
                        playback = null
                        mutableState.update { it.copy(playing = null) }
                    }
                }
            }
    }

    /** Stops the take that sounds, if any. */
    public fun stop() {
        playback?.cancel()
        playback = null
        mutableState.update { it.copy(playing = null) }
    }

    private suspend fun loadProgress() {
        try {
            val today = clock.now().toLocalDateTime(zone).date
            val stored = settings.settings.first()
            val week = weekStatus.load(today, stored.weeklyGoalDays, stored.pausedWeeks).status
            val minutes = attempts.minutesByDay(week.weekStart, zone)
            val days = (0 until DAYS_PER_WEEK).map { week.weekStart.plus(it, DateTimeUnit.DAY) }
            val learned = learner.learnedSince(week.weekStart)
            mutableState.update {
                it.copy(
                    week = week,
                    minutesByDay = days.map { day -> minutes[day] ?: 0 },
                    todayIndex = days.indexOf(today),
                    learnedThisWeek = learned,
                    compare = findCompare(),
                    storageFailed = false,
                )
            }
        } catch (_: StorageUnavailableException) {
            mutableState.update {
                it.copy(
                    week = null,
                    minutesByDay = emptyList(),
                    compare = null,
                    storageFailed = true,
                )
            }
        }
    }

    /** The piece played most recently with a first and a latest full run on different local days. */
    private suspend fun findCompare(): Compare? {
        val pieces = Curriculum.passages.filter { it.piece }
        val recentFirst = pieces.mapNotNull { piece -> attempts.lastPlayed(piece.id)?.let { piece to it } }
        for ((piece, _) in recentFirst.sortedByDescending { it.second }) {
            compareOfPiece(piece)?.let { return it }
        }
        return null
    }

    /** [piece]'s comparison, or null when it lacks a full run or both started on the same local day. */
    private suspend fun compareOfPiece(piece: Passage): Compare? {
        val first = attempts.firstFullRun(piece.id)
        val latest = attempts.latestFullRun(piece.id)
        if (first == null || latest == null) return null
        val firstDate = first.startedAt.toLocalDateTime(zone).date
        val latestDate = latest.startedAt.toLocalDateTime(zone).date
        return if (firstDate == latestDate) null else compareOf(piece, first, latest, firstDate, latestDate)
    }

    private fun compareOf(
        piece: Passage,
        first: Attempt,
        latest: Attempt,
        firstDate: LocalDate,
        latestDate: LocalDate,
    ) = Compare(
        passage = piece,
        day1 = first,
        today = latest,
        day1Date = firstDate,
        todayDate = latestDate,
        day1Lane = TakeLane.dots(piece, first),
        todayLane = TakeLane.dots(piece, latest),
    )
}
