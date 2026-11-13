package pianoanchor.platform.androidmidi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
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

    /** When false, opening any device fails (as if it went away just before). */
    var opensSucceed = true

    /** When true, open senders throw [IOException] (as a closed Android port does). */
    var sendsFail = false

    val inputsOpened = AtomicInteger(0)
    val inputsClosed = AtomicInteger(0)
    val outputsOpened = AtomicInteger(0)
    val outputsClosed = AtomicInteger(0)

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
