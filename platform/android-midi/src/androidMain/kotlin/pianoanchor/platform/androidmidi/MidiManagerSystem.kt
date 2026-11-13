package pianoanchor.platform.androidmidi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import pianoanchor.core.model.ExcludeFromCoverage
import kotlin.coroutines.resume

private const val GLUE = "thin android.media.midi glue; verified by the 1.4m owner test on a Pixel"

/** Creates the adapter over the system MIDI service of [context]. */
@ExcludeFromCoverage(GLUE)
public fun AndroidMidiAdapter(context: Context): AndroidMidiAdapter = AndroidMidiAdapter(MidiManagerSystem(context))

/**
 * [MidiSystem] over `MidiManager`. Lists byte-stream (MIDI 1.0) devices only. Callbacks run on the main looper;
 * received bytes arrive on Android's binder threads. On a device without the MIDI feature it lists nothing.
 */
@ExcludeFromCoverage(GLUE)
internal class MidiManagerSystem(
    context: Context,
) : MidiSystem {
    private val manager: MidiManager? = context.getSystemService(MidiManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val executor = context.mainExecutor

    override val devices: Flow<List<SystemDevice>> =
        callbackFlow {
            val manager = manager
            if (manager == null) {
                send(emptyList())
                awaitClose()
                return@callbackFlow
            }
            val lock = Any()
            val emitCurrent = { synchronized(lock) { trySend(currentInfos(manager).map(::toSystemDevice)) } }
            val callback =
                object : MidiManager.DeviceCallback() {
                    override fun onDeviceAdded(device: MidiDeviceInfo) {
                        emitCurrent()
                    }

                    override fun onDeviceRemoved(device: MidiDeviceInfo) {
                        emitCurrent()
                    }
                }
            register(manager, callback)
            emitCurrent() // after registering, so no change between the snapshot and the callback is missed
            awaitClose { manager.unregisterDeviceCallback(callback) }
        }

    override suspend fun openInput(
        device: SystemDevice,
        onBytes: (ByteArray, Int, Int, Long) -> Unit,
        onClosed: () -> Unit,
    ): AutoCloseable? {
        val (manager, opened, port) = openPort(device) { it.openOutputPort(0) } ?: return null
        val receiver =
            object : MidiReceiver() {
                override fun onSend(
                    msg: ByteArray,
                    offset: Int,
                    count: Int,
                    timestamp: Long,
                ) = onBytes(msg, offset, count, timestamp)
            }
        val removal =
            object : MidiManager.DeviceCallback() {
                override fun onDeviceRemoved(info: MidiDeviceInfo) {
                    if (info.id == device.systemId) onClosed()
                }
            }
        register(manager, removal)
        port.connect(receiver)
        return AutoCloseable {
            manager.unregisterDeviceCallback(removal)
            port.disconnect(receiver)
            port.close()
            opened.close()
        }
    }

    override suspend fun openOutput(device: SystemDevice): SystemSender? {
        val (_, opened, port) = openPort(device) { it.openInputPort(0) } ?: return null
        return object : SystemSender {
            override fun send(
                bytes: ByteArray,
                offset: Int,
                count: Int,
            ) = port.send(bytes, offset, count)

            override fun close() {
                port.close()
                opened.close()
            }
        }
    }

    /** Opens [device] and one of its ports with [openPort]; null (and nothing left open) if either fails. */
    private suspend fun <P> openPort(
        device: SystemDevice,
        openPort: (MidiDevice) -> P?,
    ): OpenPort<P>? {
        val manager = manager ?: return null
        val opened = open(manager, device)
        val port = opened?.let(openPort)
        if (port == null) opened?.close()
        return port?.let { OpenPort(manager, opened, it) }
    }

    @ExcludeFromCoverage(GLUE) // nested classes need their own annotation
    private data class OpenPort<P>(
        val manager: MidiManager,
        val device: MidiDevice,
        val port: P,
    )

    /** Opens the device that is still listed under [device]'s system id; null if it is gone or fails to open. */
    private suspend fun open(
        manager: MidiManager,
        device: SystemDevice,
    ): MidiDevice? {
        val info = currentInfos(manager).firstOrNull { it.id == device.systemId } ?: return null
        return suspendCancellableCoroutine { continuation ->
            manager.openDevice(
                info,
                { opened -> continuation.resume(opened) { _, value, _ -> value?.close() } },
                handler,
            )
        }
    }

    private fun register(
        manager: MidiManager,
        callback: MidiManager.DeviceCallback,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM, executor, callback)
        } else {
            @Suppress("DEPRECATION")
            manager.registerDeviceCallback(callback, handler)
        }
    }

    private fun currentInfos(manager: MidiManager): List<MidiDeviceInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
        } else {
            @Suppress("DEPRECATION")
            manager.devices.toList()
        }

    private fun toSystemDevice(info: MidiDeviceInfo): SystemDevice {
        val properties = info.properties
        return SystemDevice(
            systemId = info.id,
            transport =
                when (info.type) {
                    MidiDeviceInfo.TYPE_USB -> Transport.USB
                    MidiDeviceInfo.TYPE_BLUETOOTH -> Transport.BLUETOOTH
                    else -> Transport.OTHER
                },
            inputPorts = info.inputPortCount,
            outputPorts = info.outputPortCount,
            manufacturer = properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER),
            product = properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT),
            serial = properties.getString(MidiDeviceInfo.PROPERTY_SERIAL_NUMBER),
            name = properties.getString(MidiDeviceInfo.PROPERTY_NAME),
            bluetoothAddress = bluetoothDevice(properties)?.address,
        )
    }

    private fun bluetoothDevice(properties: Bundle): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            properties.getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            properties.getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE) as? BluetoothDevice
        }
}
