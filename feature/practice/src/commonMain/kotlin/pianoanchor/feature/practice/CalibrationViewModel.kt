package pianoanchor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pianoanchor.core.model.MidiDevice
import pianoanchor.core.model.MidiDeviceUnavailableException
import pianoanchor.core.model.NoteOn
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.CalibrationRepositoryPort
import pianoanchor.core.ports.ClockPort
import pianoanchor.core.ports.MetronomePort
import pianoanchor.core.ports.MidiInputPort
import pianoanchor.engine.Tunables
import pianoanchor.engine.calibration.CalibrationResult
import pianoanchor.engine.calibration.LatencyCalibration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Calibrates the latency of the piano [device] (`docs/algorithms/latency-calibration.md`): [start] plays the
 * calibration pattern on [metronome] (clock [time]) while the learner taps a key on each click, then judges the taps
 * ([LatencyCalibration.evaluate]) and stores an accepted offset in [calibration]. Any other result is not stored; call
 * [start] again to retry. [close] ends a run and returns once the piano is closed, so the next screen can open it (one
 * reader per input).
 */
public class CalibrationViewModel(
    private val device: MidiDevice,
    private val input: MidiInputPort,
    private val metronome: MetronomePort,
    private val time: ClockPort,
    private val calibration: CalibrationRepositoryPort,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CalibrationState())
    public val state: StateFlow<CalibrationState> = mutableState.asStateFlow()

    private var run: Job? = null
    private var retry: Job? = null

    /** Starts the clicks. Ignored while a run or a save retry is in progress; also how Try again begins a new run. */
    public fun start() {
        if (run?.isActive == true || retry?.isActive == true) return
        run = viewModelScope.launch { calibrate() }
    }

    /**
     * Stores again the accepted offset whose save failed. Ignored unless the run is DONE with an Accepted result and
     * saveFailed, and while a retry is already running.
     */
    public fun retrySave() {
        val current = mutableState.value
        val result = current.result
        if (current.phase != CalibrationPhase.DONE || !current.saveFailed) return
        if (result !is CalibrationResult.Accepted || retry?.isActive == true) return
        retry =
            viewModelScope.launch {
                val saved = save(result)
                mutableState.update { it.copy(saveFailed = !saved) }
            }
    }

    /** Ends a run or save retry, storing nothing more; returns once the metronome is stopped and the piano closed. */
    public suspend fun close() {
        retry?.cancelAndJoin()
        run?.cancelAndJoin()
    }

    private suspend fun calibrate() {
        val clicks =
            LatencyCalibration.clickTimes(
                time.nowNanos() + Tunables.TEMPO_COUNT_IN_LEAD_MS.milliseconds.inWholeNanoseconds,
            )
        val taps = mutableListOf<NoteOn>()
        mutableState.value = CalibrationState(phase = CalibrationPhase.RUNNING)
        val finished =
            try {
                listen(clicks, taps)
            } finally {
                withContext(NonCancellable) { metronome.stop() }
            }
        if (!finished) return mutableState.update { CalibrationState(disconnected = true) }
        val result = LatencyCalibration.evaluate(clicks, taps)
        val saved = result !is CalibrationResult.Accepted || save(result)
        mutableState.update {
            it.copy(
                phase = CalibrationPhase.DONE,
                pulse = false,
                result = result,
                saveFailed = !saved,
            )
        }
    }

    /**
     * Plays the clicks and collects the taps; true when the pattern and the last-tap grace ran out, false when the
     * piano's stream ended.
     */
    private suspend fun listen(
        clicks: List<Long>,
        taps: MutableList<NoteOn>,
    ): Boolean =
        coroutineScope {
            val ended = CompletableDeferred<Boolean>()
            val halfBeat = LatencyCalibration.halfBeatNanos(clicks)
            launch {
                try {
                    input.events(device).collect { event ->
                        if (event !is NoteOn) return@collect
                        taps += event
                        val counted = LatencyCalibration.countedOffsets(clicks, taps).size
                        mutableState.update { it.copy(countedTaps = counted) }
                    }
                } catch (_: MidiDeviceUnavailableException) {
                    // Not listed (or gone) when the run opened it: same as a drop.
                }
                ended.complete(false)
            }
            launch {
                metronome.start(clicks.first(), clicks[1] - clicks[0], beatsPerBar = 1)
                clicks.forEachIndexed { i, at ->
                    delayUntil(at)
                    mutableState.update { it.copy(click = i + 1, pulse = true) }
                    delayUntil(at + halfBeat)
                    mutableState.update { it.copy(pulse = false) }
                }
                delay(Tunables.CALIBRATION_LAST_TAP_GRACE_MS.milliseconds) // late-delivered taps still arrive
                ended.complete(true)
            }
            val finished = ended.await()
            coroutineContext.cancelChildren() // the reader (closing the piano) and a clock still running
            finished
        }

    private suspend fun delayUntil(nanos: Long) {
        val wait = nanos - time.nowNanos()
        if (wait > 0) delay(wait.nanoseconds)
    }

    /** Stores an accepted offset; false when the store can't be written. */
    private suspend fun save(result: CalibrationResult.Accepted): Boolean =
        try {
            calibration.setLatencyOffsetNanos(device.id, result.offsetNanos)
            true
        } catch (_: StorageUnavailableException) {
            false
        }
}
