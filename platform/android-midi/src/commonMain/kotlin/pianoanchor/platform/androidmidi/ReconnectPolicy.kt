package pianoanchor.platform.androidmidi

import pianoanchor.engine.Tunables

/**
 * Backoff for reopening a dropped Bluetooth LE MIDI device: waits `Tunables.BLE_RECONNECT_INITIAL_MS`, doubling up to
 * `BLE_RECONNECT_MAX_MS`, and gives up once the next attempt would start more than `BLE_RECONNECT_GIVE_UP_MS` after
 * the drop (counting the waits). Not thread-safe: one instance per reconnecting device.
 */
internal class ReconnectPolicy {
    private var nextMs = Tunables.BLE_RECONNECT_INITIAL_MS
    private var waitedMs = 0L

    /** The wait before the next attempt, or null to give up. */
    fun nextDelayMs(): Long? {
        if (waitedMs + nextMs > Tunables.BLE_RECONNECT_GIVE_UP_MS) return null
        val delay = nextMs
        waitedMs += delay
        nextMs = minOf(nextMs * 2, Tunables.BLE_RECONNECT_MAX_MS)
        return delay
    }

    /** Starts over, for the next drop after a successful reopen. */
    fun reset() {
        nextMs = Tunables.BLE_RECONNECT_INITIAL_MS
        waitedMs = 0L
    }
}
