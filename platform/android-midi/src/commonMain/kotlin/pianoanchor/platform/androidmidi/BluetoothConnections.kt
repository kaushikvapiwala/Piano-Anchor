package pianoanchor.platform.androidmidi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import pianoanchor.engine.Tunables
import java.util.concurrent.ConcurrentHashMap

/**
 * The Bluetooth LE devices [AndroidMidiAdapter] has connected, by device id: opens them through [system], keeps each
 * one open while it is in [listed], and reopens it in [scope] after a drop (see `docs/protocols/ble-midi.md`).
 */
internal class BluetoothConnections(
    private val system: MidiSystem,
    private val listed: Flow<List<ListedDevice>>,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()
    private val keepers = ConcurrentHashMap<String, Job>() // keep-alive job by device id

    /**
     * Opens the device at [address] and keeps it connected; true once device [id] is listed, false if it can't be
     * opened or isn't listed in time. A device that is connected and listed is left as it is; one that is waiting to
     * be reopened is opened again at once.
     */
    suspend fun connect(
        id: String,
        address: String,
    ): Boolean =
        lock.withLock {
            if (keepers.containsKey(id) && listed.first().lists(id)) return true
            keepers.remove(id)?.cancelAndJoin()
            val handle = openListed(id, address) ?: return false
            // Undispatched, so the job is inside its try block (which owns the handle) before anyone can cancel it.
            keepers[id] = scope.launch(start = CoroutineStart.UNDISPATCHED) { keepConnected(id, address, handle) }
            true
        }

    /** Stops reconnecting device [id] and closes it if open. No-op if unknown. */
    suspend fun disconnect(id: String) {
        lock.withLock { keepers.remove(id)?.cancelAndJoin() }
    }

    /**
     * Holds [first] (device [id], listed) open; after each drop closes the dead handle and reopens [address] with
     * [ReconnectPolicy] backoff. Ends, forgetting the device, when the policy gives up or the job is cancelled.
     */
    private suspend fun keepConnected(
        id: String,
        address: String,
        first: AutoCloseable,
    ) {
        val policy = ReconnectPolicy()
        var handle = first
        try {
            while (true) {
                try {
                    listed.first { !it.lists(id) }
                } finally {
                    handle.close() // dead after a drop; closing on cancel disconnects
                }
                handle = reopen(id, address, policy) ?: return
                policy.reset()
            }
        } finally {
            keepers.remove(id, currentCoroutineContext().job)
        }
    }

    /** Waits as [policy] says and tries [openListed] until it succeeds (the new handle) or the policy gives up. */
    private suspend fun reopen(
        id: String,
        address: String,
        policy: ReconnectPolicy,
    ): AutoCloseable? {
        while (true) {
            delay(policy.nextDelayMs() ?: return null)
            if (system.bluetoothProblem() == null) openListed(id, address)?.let { return it }
        }
    }

    /**
     * Opens [address] and waits up to `Tunables.BLE_LISTED_TIMEOUT_MS` for device [id] to be listed. Returns the
     * handle, or null with nothing left open.
     */
    private suspend fun openListed(
        id: String,
        address: String,
    ): AutoCloseable? {
        val handle = system.openBluetooth(address) ?: return null
        val shown =
            try {
                withTimeoutOrNull(Tunables.BLE_LISTED_TIMEOUT_MS) { listed.first { it.lists(id) } } != null
            } catch (e: CancellationException) {
                handle.close()
                throw e
            }
        if (!shown) handle.close()
        return handle.takeIf { shown }
    }
}

private fun List<ListedDevice>.lists(id: String) = any { it.device.id == id }
