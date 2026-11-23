package pianoanchor.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.Release
import pianoanchor.core.model.RunMode
import pianoanchor.core.model.Settings
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.CalibrationRepositoryPort
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.core.ports.UpdateCheckPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.learner.learnerDay
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.SessionComposer
import pianoanchor.engine.session.passageHistories
import pianoanchor.engine.update.isNewer
import kotlin.math.roundToInt
import kotlin.time.Clock

private const val MORNING_FROM_HOUR = 5
private const val AFTERNOON_FROM_HOUR = 12
private const val EVENING_FROM_HOUR = 18
private const val NANOS_PER_MILLI = 1_000_000.0

/**
 * Today: composes the day's session from [learner]'s plan and the budget in [settings], with each passage's history
 * from [attempts], and follows the connected piano (the first of [input]'s devices, with its latency offset from
 * [calibration]). Counts the practice days for the weekly goal and the 16-day exit-check line from local attempts
 * and ear reviews.
 * The day and the greeting follow [clock] in [zone]. A store that can't be read leaves [TodayState.storageFailed]
 * until [refresh].
 *
 * Once per learner day, when Today opens with the update check on in [settings], it asks [updateCheck] for the latest
 * release (the day is recorded first, so it is at most one request a day, whatever the answer). [TodayState.update]
 * is the latest release seen when it is newer than [installedVersion] and not dismissed; a failed check shows nothing.
 */
public class TodayViewModel(
    private val learner: LearnerModel,
    private val settings: SettingsRepositoryPort,
    private val attempts: AttemptRepositoryPort,
    private val input: MidiInputPort,
    private val discovery: MidiDiscoveryPort,
    private val calibration: CalibrationRepositoryPort,
    private val clock: Clock,
    private val zone: TimeZone,
    private val updateCheck: UpdateCheckPort,
    private val installedVersion: String,
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
        viewModelScope.launch { checkForUpdate() }
        viewModelScope.launch { reconnectLastPiano() }
    }

    /** Loads the plan and counts again, e.g. after a run was saved or a failed read. */
    public fun refresh() {
        load?.cancel()
        load = viewModelScope.launch { loadToday() }
    }

    /** Hides the update banner until a newer version than the one it names is released. */
    public fun dismissUpdate() {
        val release = mutableState.value.update ?: return
        mutableState.update { it.copy(update = null) }
        viewModelScope.launch {
            try {
                settings.update { it.copy(dismissedUpdate = release.version) }
            } catch (_: StorageUnavailableException) {
                // The banner stays hidden for this visit; it may come back next time.
            }
        }
    }

    /**
     * Where tapping [card] leads: Practice on a passage card's passage and rung, the ear screen with an Ear card's
     * skills, or Pianos when no piano is connected.
     */
    public fun target(card: Card): TodayTarget {
        val piano = mutableState.value.piano ?: return TodayTarget.Pianos
        return when (card) {
            is Card.PassageCard -> TodayTarget.Practice(piano.device, card.passage.id, card.rung, card.mode)
            is Card.Ear -> TodayTarget.Ear(piano.device, card.skills)
        }
    }

    private suspend fun loadToday() {
        try {
            val now = clock.now()
            val local = now.toLocalDateTime(zone)
            // The plan follows the learner day; practice days are calendar days (as the attempts store them).
            val plan = learner.plan(learnerDay(now, zone))
            val today = local.date
            val stored = settings.settings.first()
            val history =
                attempts.passageHistories(Curriculum.passages)
            val weekStart = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
            val windowStart = today.minus(PRACTICE_WINDOW_DAYS - 1, DateTimeUnit.DAY)
            val from = minOf(weekStart, windowStart)
            val days = attempts.practiceDays(from, zone) + learner.earDays(from)
            val session =
                SessionComposer.compose(
                    plan,
                    Curriculum,
                    stored.dailyBudget,
                    history,
                    learner.learnedSkills(),
                )
            mutableState.update {
                it.copy(
                    session = session,
                    greeting = greetingAt(local.hour),
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

    /**
     * Once per start: connects the piano stored in [settings] when [input] does not list it, showing
     * [TodayState.connecting] meanwhile. A failure leaves the chip at "No piano" without an error or a retry.
     */
    private suspend fun reconnectLastPiano() {
        try {
            val last = settings.settings.first().lastPiano ?: return
            if (input.inputDevices.first().any { it.id == last.id }) return
            mutableState.update { it.copy(connecting = true) }
            try {
                withTimeoutOrNull(Tunables.RECONNECT_LAST_PIANO_TIMEOUT_MS) { discovery.connect(last) }
            } catch (_: BluetoothUnavailableException) {
                // Bluetooth is off or not permitted: the chip keeps saying "No piano"; Pianos explains why.
            } catch (_: MidiDeviceUnavailableException) {
                // The piano is off or out of range. (A connect that takes longer than the timeout is the same.)
            } finally {
                mutableState.update { it.copy(connecting = false) }
            }
        } catch (_: StorageUnavailableException) {
            // Nothing is remembered that can be read: the chip keeps saying "No piano".
        }
    }

    private suspend fun checkForUpdate() {
        try {
            val day = learnerDay(clock.now(), zone)

            fun Settings.due() = updateCheckOn && lastUpdateCheck != day
            if (settings.settings.first().due()) {
                var previousCheck: LocalDate? = null
                var claimed = false
                // Check again and claim the day in one update, so two Today screens opening together ask once.
                settings.update { stored ->
                    claimed = stored.due()
                    previousCheck = stored.lastUpdateCheck
                    if (claimed) stored.copy(lastUpdateCheck = day) else stored
                }
                if (claimed) fetchLatest(day, previousCheck)
            }
            mutableState.update { it.copy(update = settings.settings.first().bannerRelease()) }
        } catch (_: StorageUnavailableException) {
            // The banner is optional: without storage there is nothing to show or remember.
        }
    }

    /**
     * Asks for the latest release and stores it. A request cut short by leaving Today gives the day back (to
     * [previousCheck]), so the next Today visit asks again instead of missing the day.
     */
    private suspend fun fetchLatest(
        day: LocalDate,
        previousCheck: LocalDate?,
    ) {
        val release =
            try {
                updateCheck.latest()
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    settings.update { if (it.lastUpdateCheck == day) it.copy(lastUpdateCheck = previousCheck) else it }
                }
                throw e
            }
        if (release != null) settings.update { it.copy(latestRelease = release) }
    }

    /** The release to announce: seen, newer than the installed version and newer than any dismissed one. */
    private fun Settings.bannerRelease(): Release? =
        latestRelease?.takeIf { release ->
            updateCheckOn &&
                isNewer(release.version, installedVersion) == true &&
                dismissedUpdate?.let { isNewer(release.version, it) } != false
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
