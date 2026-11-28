package pianoanchor.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.AttemptRepositoryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.guidance.WeakSpot
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.learner.learnerDay
import pianoanchor.engine.midi.PedalTapDetector
import pianoanchor.engine.profile
import pianoanchor.engine.session.Card
import pianoanchor.engine.session.Session
import pianoanchor.engine.session.SessionRunner
import pianoanchor.engine.session.SessionStep
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60.0
private const val MAX_LEARNED_SHOWN = 3

/**
 * The one-tap session: runs the day's [steps][SessionRunner] one after another. Each step is opened on [opens] (the
 * host navigates to the step's screen) and gets control back through [stepFinished]. Between two steps a card counts
 * down [Tunables.SESSION_NEXT_COUNTDOWN_S] seconds before the next step opens; [goNow] (or a tap of the sustain pedal,
 * which this ViewModel reads only while that card shows and `Settings.pedalControlOn` is set) opens it at once. After
 * the last step the [SessionFlowState.recap] shows what was played and learned. Finished steps are recorded in
 * [progress], so leaving with [exit] and starting again resumes at the first unfinished step.
 *
 * The one piano reader (the pedal) is closed, and joined, before a step is opened; the host also awaits
 * [releasePiano] before it leaves for another screen.
 */
@Suppress("TooManyFunctions") // One small function per control of the flow.
public class SessionFlowViewModel(
    private val learner: LearnerModel,
    private val settings: SettingsRepositoryPort,
    private val attempts: AttemptRepositoryPort,
    private val input: MidiInputPort,
    private val clock: Clock,
    private val zone: TimeZone,
    private val progress: SessionProgress,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SessionFlowState(emptyList(), 0))
    public val state: StateFlow<SessionFlowState> = mutableState.asStateFlow()

    private val openings = Channel<TodayTarget>(Channel.BUFFERED)

    /** Where the host goes next: the screen of the step to run, or Pianos when no piano is connected. */
    public val opens: Flow<TodayTarget> = openings.receiveAsFlow()

    private var countdown: Job? = null
    private var pedalReader: Job? = null
    private var startedAt: Instant = clock.now()
    private var learnedBefore: Set<String> = emptySet()
    private var stepsDone = 0
    private var finishedPassages = emptyList<Card.PassageCard>()
    private var awaitingStep = false

    private val day: LocalDate get() = learnerDay(clock.now(), zone)

    /**
     * Starts the run of [session] without the steps of the cards at the indices [skipped] in [Session.cards] and
     * without the steps already finished today, and opens the first step.
     */
    public fun start(
        session: Session,
        skipped: Set<Int>,
    ) {
        // A day already completed starts the whole session again; one left midway resumes after its finished steps.
        val finished = if (progress.completedOn(day)) emptySet() else progress.finishedOn(day)
        val steps = SessionRunner.steps(session, skipped).filter { it.key() !in finished }
        startedAt = clock.now()
        stepsDone = 0
        finishedPassages = emptyList()
        awaitingStep = false
        mutableState.value = SessionFlowState(steps, 0)
        viewModelScope.launch {
            learnedBefore = learnedNow()
            if (steps.isEmpty()) showRecap() else open(0)
        }
    }

    /** The step on screen is done: the card between steps shows, or the recap after the last. */
    public fun stepFinished() {
        val flow = mutableState.value
        val step = flow.steps.getOrNull(flow.current)
        if (!awaitingStep || step == null) return
        awaitingStep = false
        stepsDone++
        progress.markFinished(day, step)
        (step.card as? Card.PassageCard)?.let { finishedPassages = finishedPassages + it }
        val next = flow.steps.getOrNull(flow.current + 1)
        viewModelScope.launch {
            if (next == null) showRecap() else showBetween(next, Tunables.SESSION_NEXT_COUNTDOWN_S, paused = false)
        }
    }

    /** Freezes the countdown on the card between steps. */
    public fun pauseCountdown() {
        val between = mutableState.value.between?.takeIf { !it.paused } ?: return
        countdown?.cancel()
        mutableState.update { it.copy(between = between.copy(paused = true)) }
    }

    /** Continues a frozen countdown. */
    public fun resumeCountdown() {
        val between = mutableState.value.between?.takeIf { it.paused } ?: return
        mutableState.update { it.copy(between = between.copy(paused = false)) }
        startCountdown()
    }

    /** Leaves out the step the card between steps announces; the one after it (or the recap) comes next. */
    public fun skipStep() {
        val flow = mutableState.value
        val between = flow.between ?: return
        val steps = flow.steps.filterIndexed { index, _ -> index != flow.current + 1 }
        val next = steps.getOrNull(flow.current + 1)
        if (next == null) {
            // Stop the countdown now: a tick before the recap shows must not open a step past the list.
            countdown?.cancel()
            mutableState.value = flow.copy(steps = steps, between = null)
            viewModelScope.launch { showRecap() }
        } else {
            mutableState.value = flow.copy(steps = steps, between = between.copy(next = next))
        }
    }

    /** Opens the next step now, without waiting for the countdown. */
    public fun goNow() {
        val flow = mutableState.value
        if (flow.between == null) return
        val index = flow.current + 1
        mutableState.value = flow.copy(current = index, between = null)
        viewModelScope.launch { open(index) }
    }

    /** Back to Today: stops the countdown and the pedal reader. Steps finished so far stay done. */
    public fun exit() {
        awaitingStep = false
        viewModelScope.launch {
            releasePiano()
            mutableState.value = SessionFlowState(emptyList(), 0)
        }
    }

    /** Stops the countdown and the pedal reader, and returns once the piano is closed (one reader per input). */
    public suspend fun releasePiano() {
        withContext(NonCancellable) {
            countdown?.cancelAndJoin()
            pedalReader?.cancelAndJoin()
        }
        countdown = null
        pedalReader = null
    }

    private suspend fun open(index: Int) {
        releasePiano()
        mutableState.update { it.copy(current = index, between = null, pedalHint = false) }
        val device = input.inputDevices.first().firstOrNull()
        awaitingStep = device != null
        openings.send(device?.let { targetOf(mutableState.value.steps[index].card, it) } ?: TodayTarget.Pianos)
    }

    private suspend fun showBetween(
        next: SessionStep,
        secondsLeft: Int,
        paused: Boolean,
    ) {
        val reads = pedalControlOn() && input.inputDevices.first().isNotEmpty()
        mutableState.update { it.copy(between = Between(next, secondsLeft, paused), pedalHint = reads) }
        if (!paused) startCountdown()
        if (reads) pedalReader = viewModelScope.launch { readPedal() }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown =
            viewModelScope.launch {
                while ((mutableState.value.between?.secondsLeft ?: 0) > 0) {
                    delay(MILLIS_PER_SECOND)
                    mutableState.update { flow ->
                        flow.copy(between = flow.between?.let { it.copy(secondsLeft = it.secondsLeft - 1) })
                    }
                }
                goNow()
            }
    }

    private suspend fun readPedal() {
        val device = input.inputDevices.first().firstOrNull() ?: return
        val detector = PedalTapDetector()
        try {
            input.events(device).collect { event -> if (detector.onEvent(event)) goNow() }
        } catch (_: MidiDeviceUnavailableException) {
            // The piano is gone: the countdown goes on, and the next step reports no piano.
        }
    }

    private suspend fun pedalControlOn(): Boolean =
        try {
            settings.settings.first().pedalControlOn
        } catch (_: StorageUnavailableException) {
            false
        }

    private suspend fun showRecap() {
        releasePiano()
        val recap =
            SessionRecap(
                minutes = ((clock.now() - startedAt).inWholeSeconds / SECONDS_PER_MINUTE).roundToInt(),
                stepsDone = stepsDone,
                learned = newlyLearned(),
                tip = weakestSpot(),
            )
        progress.markCompleted(day)
        mutableState.update { it.copy(between = null, recap = recap, pedalHint = false) }
    }

    private suspend fun learnedNow(): Set<String> =
        try {
            learner.learnedSkills()
        } catch (_: StorageUnavailableException) {
            emptySet()
        }

    /** The skills that became learned during the run, at most [MAX_LEARNED_SHOWN]. */
    private suspend fun newlyLearned() =
        (learnedNow() - learnedBefore).mapNotNull { Curriculum.graph.byId(it) }.take(MAX_LEARNED_SHOWN)

    /**
     * The worst spot of the run: the bars with the most findings in the least accurate unclean run of a passage step
     * played during this session, or null.
     */
    private suspend fun weakestSpot(): RecapTip? =
        try {
            val strictness =
                settings.settings
                    .first()
                    .strictness
                    .profile()
            finishedPassages
                .mapNotNull { card -> latestRun(card)?.let { card to it } }
                .sortedBy { (_, run) -> run.accuracy }
                .firstNotNullOfOrNull { (card, run) ->
                    WeakSpot.of(card.passage, run, strictness)?.let { RecapTip(card.passage.title, it) }
                }
        } catch (_: StorageUnavailableException) {
            null
        }

    private suspend fun latestRun(card: Card.PassageCard): Attempt? =
        attempts
            .recent(card.passage.id, 1)
            .first()
            .firstOrNull()
            ?.takeIf { it.startedAt >= startedAt }
}
