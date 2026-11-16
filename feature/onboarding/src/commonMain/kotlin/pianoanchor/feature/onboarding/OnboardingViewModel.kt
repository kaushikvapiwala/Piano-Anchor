package pianoanchor.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.MidiNote
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.model.Tone
import pianoanchor.core.ports.MidiDiscoveryPort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.core.ports.MidiOutputPort
import pianoanchor.core.ports.SettingsRepositoryPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.content.Curriculum
import pianoanchor.engine.learner.LearnerModel
import pianoanchor.engine.learner.learnerDay
import pianoanchor.engine.midi.playTones
import pianoanchor.engine.onboarding.echoCanStillMatch
import pianoanchor.engine.onboarding.echoMatches
import kotlin.time.Clock

/** C4, E4 and G4: the chord the piano plays and the learner echoes (in any octave). */
private val CHORD = listOf(MidiNote(60), MidiNote(64), MidiNote(67))

/**
 * First-launch onboarding (`docs/ui/living-studio.md`): the first note on any listed [input] picks the piano (while
 * [discovery] connects the Bluetooth pianos it finds); the piano plays C-E-G through [output] and the learner echoes
 * it; the route then hosts the tap calibration; the start point places the skills of the units before it with
 * [learner] and stores `onboarded` in [settings]. The day follows [clock] in [zone].
 *
 * An input has one reader at a time: [close] returns once the piano is closed, and the next session or the
 * calibration starts only after the reader before it has closed. When the ViewModel is cleared, [cleanup] runs
 * [release], which disconnects the Bluetooth pianos connected for the search that were not chosen.
 */
@Suppress("TooManyFunctions") // One small function per step of the flow, as in PracticeViewModel.
public class OnboardingViewModel(
    private val input: MidiInputPort,
    private val output: MidiOutputPort,
    private val discovery: MidiDiscoveryPort,
    private val learner: LearnerModel,
    private val settings: SettingsRepositoryPort,
    private val clock: Clock,
    private val zone: TimeZone,
    private val cleanup: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingState())
    public val state: StateFlow<OnboardingState> = mutableState.asStateFlow()

    /** Connecting, then the echo: the only reader of the piano while it runs. */
    private var session: Job? = null
    private var scan: Job? = null
    private var starting: Job? = null
    private val connectedByScan = HashSet<MidiDevice>()

    init {
        session = viewModelScope.launch { run(null) }
    }

    /** Scans for Bluetooth pianos and connects each one found, so its first note can pick it (once at a time). */
    public fun scanBluetooth() {
        // Once a piano is picked the search is over: a late permission answer must not connect more.
        if (scan?.isActive == true || state.value.device != null) return
        scan =
            viewModelScope.launch {
                try {
                    discovery.scan().collect { found ->
                        for (device in found.filter { it !in connectedByScan }) {
                            connectedByScan += device
                            launch { connect(device) }
                        }
                    }
                } catch (_: BluetoothUnavailableException) {
                    // No permission or Bluetooth off: the hint offers the piano list, where the monitor says why.
                }
            }
    }

    /** Plays C-E-G again and starts a fresh attempt (the Replay button). */
    public fun replay() {
        val device = state.value.device ?: return
        if (state.value.step != OnboardingStep.ECHO) return
        launchSession { run(device) }
    }

    /**
     * Leaves the echo for the calibration once it passed, or after `ONBOARDING_MISSES_BEFORE_SKIP` misses (Skip);
     * ignored otherwise. Closes the piano first: the calibration opens it.
     */
    public fun continueToCalibration() {
        val current = state.value
        if (current.step != OnboardingStep.ECHO || !(current.echo == EchoPhase.PASSED || current.canSkipEcho)) return
        launchSession { mutableState.update { it.copy(step = OnboardingStep.CALIBRATE) } }
    }

    /** The calibration is over (done or skipped): on to the start point. */
    public fun calibrationFinished() {
        mutableState.update {
            if (it.step == OnboardingStep.CALIBRATE) it.copy(step = OnboardingStep.START_POINT) else it
        }
    }

    /**
     * Places every skill of the units before [point], stores `onboarded` and sets [OnboardingState.finished]. A store
     * that can't be written sets [OnboardingState.storageFailed]; choosing again retries (placed skills stay).
     */
    public fun start(point: StartPoint) {
        if (state.value.step != OnboardingStep.START_POINT || starting?.isActive == true) return
        val previous = session
        starting =
            viewModelScope.launch {
                withContext(NonCancellable) { previous?.cancelAndJoin() }
                try {
                    val skills = Curriculum.units.take(point.unitsPlaced).flatMapTo(HashSet()) { it.skills }
                    learner.place(skills, learnerDay(clock.now(), zone))
                    settings.update { it.copy(onboarded = true) }
                    mutableState.update { it.copy(storageFailed = false, finished = true) }
                } catch (_: StorageUnavailableException) {
                    mutableState.update { it.copy(storageFailed = true) }
                }
            }
    }

    /** Stops reading the piano and returns once it is closed, e.g. before the piano list opens it. */
    public suspend fun close() {
        withContext(NonCancellable) { session?.cancelAndJoin() }
    }

    /** Disconnects every Bluetooth piano the search connected except the chosen one (all of them if none was). */
    public suspend fun release() {
        val chosen = state.value.device
        scan?.cancelAndJoin()
        for (other in connectedByScan.filter { it.id != chosen?.id }) discovery.disconnect(other)
        connectedByScan.removeAll { it.id != chosen?.id }
    }

    override fun onCleared() {
        cleanup.launch { release() }
    }

    /**
     * Runs [block] as the session once the previous one has ended and its reader is closed. The wait can't be
     * cancelled, so a quick second tap can't open the piano while an earlier reader is still open.
     */
    private fun launchSession(block: suspend () -> Unit) {
        val previous = session
        session =
            viewModelScope.launch {
                withContext(NonCancellable) { previous?.cancelAndJoin() }
                block()
            }
    }

    /** Waits for a piano when [known] is null, then runs the echo on it; goes back to waiting when it drops. */
    private suspend fun run(known: MidiDevice?) {
        var device = known
        while (true) {
            val piano = device ?: awaitPiano()
            if (echo(piano)) return
            device = null
            mutableState.value = OnboardingState()
        }
    }

    /** Listens to every listed input until one sends a note: that piano. Closes every reader before it returns. */
    private suspend fun awaitPiano(): MidiDevice {
        val picked =
            coroutineScope {
                val first = CompletableDeferred<MidiDevice>()
                val reading = HashSet<String>()
                launch {
                    delay(Tunables.ONBOARDING_CONNECT_HINT_MS)
                    mutableState.update { it.copy(pianoListHint = true) }
                }
                launch {
                    input.inputDevices.collect { devices ->
                        for (device in devices.filter { reading.add(it.id) }) {
                            launch {
                                try {
                                    // A flow that ends without a note (the device dropped) picks nothing.
                                    if (input.events(device).filterIsInstance<NoteOn>().firstOrNull() != null) {
                                        first.complete(device)
                                    }
                                } catch (_: MidiDeviceUnavailableException) {
                                    // Gone since it was listed: the next list decides.
                                } finally {
                                    reading.remove(device.id)
                                }
                            }
                        }
                    }
                }
                first.await().also { coroutineContext.cancelChildren() }
            }
        mutableState.update { it.copy(device = picked, pianoListHint = false) }
        // The search is over: stop scanning, and let go of the Bluetooth pianos connected for it but not chosen.
        release()
        return picked
    }

    private suspend fun connect(device: MidiDevice) {
        try {
            discovery.connect(device)
        } catch (_: BluetoothUnavailableException) {
            // As for the scan.
        } catch (_: MidiDeviceUnavailableException) {
            // Not found again or can't be opened: it never lists, so it never picks.
        }
    }

    /**
     * The echo on [device]: C-E-G out (if the piano has an output), then the learner's attempts in. True once an
     * attempt matched; false when the piano dropped.
     */
    private suspend fun echo(device: MidiDevice): Boolean {
        mutableState.update { it.copy(step = OnboardingStep.ECHO, echo = EchoPhase.PLAYING, keysHighlighted = false) }
        val played = hasOutput(device) && output.playChord(device)
        mutableState.update { it.copy(echo = EchoPhase.LISTENING, keysHighlighted = !played) }
        val attempt = ArrayList<NoteOn>()
        val matched =
            try {
                input.events(device).filterIsInstance<NoteOn>().firstOrNull { note ->
                    attempt += note
                    mutableState.update { if (it.echo == EchoPhase.MISSED) it.copy(echo = EchoPhase.LISTENING) else it }
                    when {
                        echoMatches(attempt) -> {
                            true
                        }

                        echoCanStillMatch(attempt) -> {
                            false
                        }

                        else -> {
                            attempt.clear()
                            // The note that broke the attempt may be the start of the next one (C E C E G).
                            if (echoCanStillMatch(listOf(note))) attempt += note
                            mutableState.update { it.copy(echo = EchoPhase.MISSED, misses = it.misses + 1) }
                            false
                        }
                    }
                } != null
            } catch (_: MidiDeviceUnavailableException) {
                false
            }
        if (matched) mutableState.update { it.copy(echo = EchoPhase.PASSED) }
        return matched
    }

    private suspend fun hasOutput(device: MidiDevice): Boolean = output.outputDevices.first().any { it.id == device.id }
}

/**
 * Plays [CHORD] on [device] as an arpeggio: NoteOns `PLAYBACK_NOTE_GAP_MS` apart, each held
 * `PLAYBACK_NOTE_LENGTH_MS`. False if the piano went away.
 */
private suspend fun MidiOutputPort.playChord(device: MidiDevice): Boolean =
    try {
        playTones(
            device,
            CHORD.mapIndexed { i, note ->
                Tone(note, i * Tunables.PLAYBACK_NOTE_GAP_MS, Tunables.PLAYBACK_NOTE_LENGTH_MS)
            },
        )
        true
    } catch (_: MidiDeviceUnavailableException) {
        false
    }
