package pianoanchor.platform.androidmidi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import pianoanchor.core.model.BluetoothUnavailableException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** A USB piano with one input and one output port and all identity properties. */
internal val PIANO =
    SystemDevice(
        systemId = 1,
        transport = Transport.USB,
        inputPorts = 1,
        outputPorts = 1,
        manufacturer = "Acme",
        product = "Stage Piano",
        serial = "SN1",
        name = "Acme Stage Piano",
    )

/** First system id given to opened Bluetooth devices, clear of the USB fixtures' ids. */
private const val BLUETOOTH_SYSTEM_IDS = 100

/**
 * In-memory [MidiSystem]. Like Android, it calls back on another thread: [deliver] and [unplug] run the adapter's
 * callbacks on `Dispatchers.Default` and return once they have finished. Device-list changes stay on the caller's
 * thread, so virtual time can't run on while a test waits for a callback.
 */
internal class FakeMidiSystem(
    vararg initial: SystemDevice,
) : MidiSystem {
    private val list = MutableStateFlow(initial.toList())
    private val inputs = ConcurrentHashMap<Int, OpenInput>()
    private val nextStamp = AtomicLong(0)
    private val sentBytes = CopyOnWriteArrayList<ByteArray>()
    private val advertising = MutableStateFlow(emptyList<BluetoothAdvertisement>())
    private val nextSystemId = AtomicInteger(BLUETOOTH_SYSTEM_IDS)

    /** When false, opening any device fails (as if it went away just before). */
    var opensSucceed = true

    /** When false, closing a Bluetooth handle leaves the device listed (Android is slow to drop it). */
    var closingUnlists = true

    /** When true, only [openInput] fails (the device is listed but its port can't be opened). */
    var inputsFail = false

    /** While set, [openBluetooth] waits for it to complete before it opens anything (a link that never comes up). */
    var bluetoothOpenGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    /** When true, open senders throw [IOException] (as a closed Android port does). */
    var sendsFail = false

    val inputsOpened = AtomicInteger(0)
    val inputsClosed = AtomicInteger(0)
    val outputsOpened = AtomicInteger(0)
    val outputsClosed = AtomicInteger(0)
    val bluetoothOpened = AtomicInteger(0)
    val bluetoothClosed = AtomicInteger(0)

    /** What [bluetoothProblem] reports; null means Bluetooth LE works. */
    var bluetoothState: BluetoothUnavailableException.Reason? = null

    /** When true, [scanBluetooth] fails as if the platform refused to start the scan. */
    var scansFail = false

    /** When false, [openBluetooth] succeeds but the device never shows up in [devices] (link lost in between). */
    var listsOpenedBluetooth = true

    /** Every byte range handed to an open sender, in order (copies). */
    val sent: List<ByteArray> get() = sentBytes.toList()

    override val devices: StateFlow<List<SystemDevice>> = list

    override suspend fun openInput(
        device: SystemDevice,
        onBytes: (ByteArray, Int, Int, Long) -> Unit,
        onClosed: () -> Unit,
    ): AutoCloseable? {
        if (!opensSucceed || inputsFail) return null
        inputsOpened.incrementAndGet()
        inputs[device.systemId] = OpenInput(onBytes, onClosed)
        return AutoCloseable {
            inputs.remove(device.systemId)
            inputsClosed.incrementAndGet()
        }
    }

    override suspend fun openOutput(device: SystemDevice): SystemSender? {
        if (!opensSucceed) return null
        outputsOpened.incrementAndGet()
        return object : SystemSender {
            override fun send(
                bytes: ByteArray,
                offset: Int,
                count: Int,
            ) {
                if (sendsFail) throw IOException("port closed")
                sentBytes.add(bytes.copyOfRange(offset, offset + count))
            }

            override fun close() {
                outputsClosed.incrementAndGet()
            }
        }
    }

    override fun bluetoothProblem(): BluetoothUnavailableException.Reason? = bluetoothState

    override fun scanBluetooth(): Flow<BluetoothAdvertisement> =
        flow {
            if (scansFail) throw BluetoothUnavailableException(BluetoothUnavailableException.Reason.BLUETOOTH_OFF)
            advertising.collect { all -> all.forEach { emit(it) } }
        }

    /** Lists an advertising device as a new Bluetooth system device (new system id, as Android does each time). */
    override suspend fun openBluetooth(address: String): AutoCloseable? {
        bluetoothOpenGate?.await()
        val advertisement = advertising.value.firstOrNull { it.address == address }
        if (!opensSucceed || advertisement == null) return null
        bluetoothOpened.incrementAndGet()
        val device =
            SystemDevice(
                systemId = nextSystemId.getAndIncrement(),
                transport = Transport.BLUETOOTH,
                inputPorts = 1,
                outputPorts = 1,
                name = advertisement.name,
                bluetoothAddress = address,
            )
        if (listsOpenedBluetooth) plugIn(device)
        return AutoCloseable {
            bluetoothClosed.incrementAndGet()
            inputs[device.systemId]?.onClosed?.invoke()
            if (closingUnlists) list.update { devices -> devices - device }
        }
    }

    /** Adds an advertisement (repeats allowed); every running scan sees all current ones again. */
    fun advertise(advertisement: BluetoothAdvertisement) = advertising.update { it + advertisement }

    /**
     * The link of the connected Bluetooth device at [address] drops, as Android reports it: its input is told, then
     * the device is removed. Unlike [unplug] the input is told on the caller's thread: a reader that waits for the
     * reopen starts its grace timer inside that callback, and while the test body waits on another thread virtual
     * time would skip past the timer.
     */
    fun dropBluetooth(address: String) {
        list.value.filter { it.bluetoothAddress == address }.forEach { device ->
            inputs[device.systemId]?.onClosed?.invoke()
            list.update { devices -> devices - device }
        }
    }

    /** Only the open input of the Bluetooth device at [address] is told it closed; the device stays listed. */
    fun closeBluetoothInput(address: String) {
        list.value.filter { it.bluetoothAddress == address }.forEach { inputs[it.systemId]?.onClosed?.invoke() }
    }

    /** Only removes the Bluetooth device at [address] from the list (its input was told earlier or never). */
    fun unlistBluetooth(address: String) = list.update { all -> all.filterNot { it.bluetoothAddress == address } }

    /** The device with [systemId] sends `bytes[offset until offset + count]` in one platform callback. */
    suspend fun deliver(
        systemId: Int,
        bytes: ByteArray,
        timestampNanos: Long = nextStamp.incrementAndGet(),
        offset: Int = 0,
        count: Int = bytes.size - offset,
    ) {
        withContext(Dispatchers.Default) { inputs[systemId]?.onBytes?.invoke(bytes, offset, count, timestampNanos) }
    }

    /**
     * Tells the open input of the device with [systemId], if any, then removes the device from the list. The list
     * changes on the caller's thread, after the hop: changed while the test body waits on another thread, `runTest`
     * would see only background work and skip virtual time through the adapter's reconnect backoff.
     */
    suspend fun unplug(systemId: Int) {
        withContext(Dispatchers.Default) { inputs[systemId]?.onClosed?.invoke() }
        list.update { devices -> devices.filterNot { it.systemId == systemId } }
    }

    fun plugIn(device: SystemDevice) = list.update { it + device }

    private class OpenInput(
        val onBytes: (ByteArray, Int, Int, Long) -> Unit,
        val onClosed: () -> Unit,
    )
}
