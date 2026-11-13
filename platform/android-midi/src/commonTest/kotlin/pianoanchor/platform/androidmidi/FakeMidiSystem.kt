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
 * callbacks on `Dispatchers.Default` and return once they have finished.
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
        if (!opensSucceed) return null
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
            list.update { devices -> devices - device }
        }
    }

    /** Adds an advertisement (repeats allowed); every running scan sees all current ones again. */
    fun advertise(advertisement: BluetoothAdvertisement) = advertising.update { it + advertisement }

    /** The link of the connected Bluetooth device at [address] drops, as Android reports it: the device is removed. */
    suspend fun dropBluetooth(address: String) {
        list.value.filter { it.bluetoothAddress == address }.forEach { unplug(it.systemId) }
    }

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

    /** Removes the device with [systemId] from the list and tells its open input, if any. */
    suspend fun unplug(systemId: Int) {
        withContext(Dispatchers.Default) {
            list.update { devices -> devices.filterNot { it.systemId == systemId } }
            inputs[systemId]?.onClosed?.invoke()
        }
    }

    fun plugIn(device: SystemDevice) = list.update { it + device }

    private class OpenInput(
        val onBytes: (ByteArray, Int, Int, Long) -> Unit,
        val onClosed: () -> Unit,
    )
}
