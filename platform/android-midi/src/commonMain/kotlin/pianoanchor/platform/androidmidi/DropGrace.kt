package pianoanchor.platform.androidmidi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pianoanchor.engine.Tunables

/** A device that left the system list while kept; [expiry] ends its grace period. */
private class Held(
    val device: ListedDevice,
) {
    lateinit var expiry: Job
}

/**
 * The device list as readers of `inputDevices` see it: [devices] identified as `identify` does, except that a device
 * in [kept] (a Bluetooth device the adapter reconnects) that leaves it is held for up to `Tunables.BLE_DROP_GRACE_MS`
 * (see `docs/protocols/ble-midi.md`). A held device goes at once if it is no longer kept (disconnected, or the
 * reconnecting gave up) and is replaced by the real entry if the system lists it again. Other devices, USB included,
 * leave when the system says so.
 *
 * The hold is shared by every reader: [view] always has the current list, also for a reader that starts in the gap.
 * It follows [devices] and [kept] in [scope] from the moment it is created, on whichever thread changes them, so the
 * view is current as soon as a change returns. Timers run in [scope] with its dispatcher.
 */
internal class DropGrace(
    private val devices: StateFlow<List<SystemDevice>>,
    private val kept: StateFlow<Set<String>>,
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private val held = mutableMapOf<String, Held>()
    private var latest = identify(devices.value)
    private var keptNow = kept.value
    private val current = MutableStateFlow(latest)

    /** The listed devices, held ones last. */
    val view: StateFlow<List<ListedDevice>> = current

    init {
        // Unconfined: a change is applied on the thread that makes it, before the change returns.
        scope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            devices.collect { onListing(identify(it)) }
        }
        scope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            kept.collect { ids -> update { keptNow = ids } }
        }
    }

    private fun onListing(now: List<ListedDevice>) =
        update {
            val before = latest
            latest = now
            val ids = now.map { it.device.id }.toSet()
            before.filter { it.device.id !in ids && it.device.id in keptNow }.forEach { hold(it) }
        }

    private fun hold(gone: ListedDevice) {
        val entry = Held(gone)
        held[gone.device.id] = entry
        entry.expiry =
            scope.launch {
                delay(Tunables.BLE_DROP_GRACE_MS)
                update { held.values.remove(entry) } // only this hold, never a newer one
            }
    }

    /** Applies [change] to the state, releases the holds that are over and publishes the view. */
    private fun update(change: () -> Unit) =
        synchronized(lock) {
            change()
            val ids = latest.map { it.device.id }.toSet()
            held.filterKeys { it in ids || it !in keptNow }.forEach { (id, released) ->
                held.remove(id)
                released.expiry.cancel()
            }
            current.value = latest + held.values.map { it.device }
        }
}
