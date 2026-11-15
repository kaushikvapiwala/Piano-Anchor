package pianoanchor.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.CalibrationRepositoryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.learner.learnerDay
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.PassageHistory
import pianoanchor.engine.session.SessionComposer
import kotlin.math.roundToInt
import kotlin.time.Clock

private const val MORNING_FROM_HOUR = 5
private const val AFTERNOON_FROM_HOUR = 12
private const val EVENING_FROM_HOUR = 18
private const val NANOS_PER_MILLI = 1_000_000.0

/**
 * Today: composes the day's session from [learner]'s plan and the budget in [settings], with each passage's history
 * from [attempts], and follows the connected piano (the first of [input]'s devices, with its latency offset from
 * [calibration]). Counts the practice days for the weekly goal and the 16-day exit-check line from local attempts.
 * The day and the greeting follow [clock] in [zone]. A store that can't be read leaves [TodayState.storageFailed]
 * until [refresh].
 */
public class TodayViewModel(
    private val learner: LearnerModel,
    private val settings: SettingsRepositoryPort,
    private val attempts: AttemptRepositoryPort,
    input: MidiInputPort,
    private val calibration: CalibrationRepositoryPort,
    private val clock: Clock,
    private val zone: TimeZone,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TodayState())
    public val state: StateFlow<TodayState> = mutableState.asStateFlow()

    private var load: Job? = null

    init {
        viewModelScope.launch {
            input.inputDevices.collect { devices ->
                val piano = devices.firstOrNull()?.let { PianoChip(it, transportOf(it), latencyMsOf(it)) }
                mutableState.update { it.copy(piano = piano) }
            }
        }
        refresh()
    }

    /** Loads the plan and counts again, e.g. after a run was saved or a failed read. */
    public fun refresh() {
        load?.cancel()
        load = viewModelScope.launch { loadToday() }
    }

    /** Where tapping [card] leads: Practice on its passage and rung, or Pianos when no piano is connected. */
    public fun target(card: Card): TodayTarget {
        val piano = mutableState.value.piano ?: return TodayTarget.Pianos
        return TodayTarget.Practice(piano.device, card.passage.id, card.rung)
    }

    private suspend fun loadToday() {
        try {
            val now = clock.now()
            val today = learnerDay(now, zone)
            val plan = learner.plan(today)
            val stored = settings.settings.first()
            val history =
                Curriculum.units
                    .flatMap { it.passages }
                    .associate { it.id to PassageHistory(attempts.lastPlayed(it.id), attempts.cleanTempos(it.id)) }
            val weekStart = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
            val windowStart = today.minus(PRACTICE_WINDOW_DAYS - 1, DateTimeUnit.DAY)
            val days = attempts.practiceDays(minOf(weekStart, windowStart), zone)
            val session = SessionComposer.compose(plan, Curriculum, stored.dailyBudget, history)
            mutableState.update {
                it.copy(
                    session = session,
                    greeting = greetingAt(now.toLocalDateTime(zone).hour),
                    daysThisWeek = days.count { day -> day in weekStart..today },
                    weeklyGoalDays = stored.weeklyGoalDays,
                    practicedDays = days.count { day -> day in windowStart..today },
                    storageFailed = false,
                )
            }
        } catch (_: StorageUnavailableException) {
            mutableState.update { it.copy(session = null, storageFailed = true) }
        }
    }

    private suspend fun latencyMsOf(device: MidiDevice): Int? =
        try {
            calibration.latencyOffsetNanos(device.id)?.let { (it / NANOS_PER_MILLI).roundToInt() }
        } catch (_: StorageUnavailableException) {
            null
        }
}

private fun transportOf(device: MidiDevice): Transport? =
    when {
        device.id.startsWith("usb:") -> Transport.USB
        device.id.startsWith("ble:") -> Transport.BLE
        else -> null
    }

private fun greetingAt(hour: Int): Greeting =
    when (hour) {
        in MORNING_FROM_HOUR until AFTERNOON_FROM_HOUR -> Greeting.MORNING
        in AFTERNOON_FROM_HOUR until EVENING_FROM_HOUR -> Greeting.AFTERNOON
        else -> Greeting.EVENING
    }
