package pianoanchor.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import pianoanchor.core.model.StorageUnavailableException
import pianoanchor.core.ports.CalibrationRepositoryPort

/** In-memory [CalibrationRepositoryPort]: safe to call from any thread, forgets everything when the process ends. */
class FakeCalibrationRepository : CalibrationRepositoryPort {
    private val offsets = MutableStateFlow(emptyMap<String, Long>())

    /** While true, every call throws [StorageUnavailableException]. */
    var failing: Boolean = false

    /** While set, [setLatencyOffsetNanos] waits for it to complete before it checks [failing] and writes. */
    var gate: CompletableDeferred<Unit>? = null

    /** How many times [setLatencyOffsetNanos] was called, failed or not. */
    var setCalls: Int = 0
        private set

    override suspend fun latencyOffsetNanos(deviceId: String): Long? {
        failIfFailing("latencyOffsetNanos")
        return offsets.value[deviceId]
    }

    override suspend fun setLatencyOffsetNanos(
        deviceId: String,
        offsetNanos: Long,
    ) {
        setCalls++
        gate?.await()
        failIfFailing("setLatencyOffsetNanos")
        offsets.update { it + (deviceId to offsetNanos) }
    }

    private fun failIfFailing(operation: String) {
        if (failing) throw StorageUnavailableException("$operation failed")
    }
}
