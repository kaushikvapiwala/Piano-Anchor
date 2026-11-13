package pianoanchor.platform.androidmidi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import pianoanchor.core.model.BluetoothUnavailableException
import pianoanchor.core.model.BluetoothUnavailableException.Reason
import pianoanchor.core.model.ExcludeFromCoverage
import kotlin.coroutines.resume

private const val GLUE = "thin android.media.midi glue; verified by the 1.4m owner test on a Pixel"

/** Service UUID that Bluetooth LE MIDI devices advertise (BLE MIDI specification). */
private const val BLE_MIDI_SERVICE_UUID = "03B80E5A-EDE8-4B33-A751-6CE34EC4C700"

/**
 * Creates the adapter over the system MIDI service of [context]. It lives as long as the app: Bluetooth LE devices it
 * connected are reopened after a drop in a scope of its own.
 */
@ExcludeFromCoverage(GLUE)
public fun AndroidMidiAdapter(context: Context): AndroidMidiAdapter =
    AndroidMidiAdapter(MidiManagerSystem(context), CoroutineScope(SupervisorJob() + Dispatchers.Default))

/**
 * [MidiSystem] over `MidiManager`. Lists byte-stream (MIDI 1.0) devices only. One device callback, registered for
 * the life of this object (the adapter is app-scoped), keeps [devices] current, so reading it costs nothing.
 * Callbacks run on the main looper; received bytes arrive on Android's binder threads. On a device without the
 * MIDI feature it lists nothing.
 */
@ExcludeFromCoverage(GLUE)
internal class MidiManagerSystem(
    context: Context,
) : MidiSystem {
    private val context: Context = context.applicationContext ?: context
    private val manager: MidiManager? = context.getSystemService(MidiManager::class.java)
    private val bluetooth: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val handler = Handler(Looper.getMainLooper())
    private val executor = context.mainExecutor
    private val list = MutableStateFlow<List<SystemDevice>>(emptyList())

    override val devices: StateFlow<List<SystemDevice>> = list

    init {
        manager?.let { manager ->
            register(
                manager,
                object : MidiManager.DeviceCallback() {
                    override fun onDeviceAdded(device: MidiDeviceInfo) = refresh(manager)

                    override fun onDeviceRemoved(device: MidiDeviceInfo) = refresh(manager)
                },
            )
            refresh(manager) // after registering, so no change between the snapshot and the callback is missed
        }
    }

    override suspend fun openInput(
        device: SystemDevice,
        onBytes: (ByteArray, Int, Int, Long) -> Unit,
        onClosed: () -> Unit,
    ): AutoCloseable? {
        val manager = manager ?: return null
        val receiver =
            object : MidiReceiver() {
                override fun onSend(
                    msg: ByteArray,
                    offset: Int,
                    count: Int,
                    timestamp: Long,
                ) = onBytes(msg, offset, count, timestamp)
            }
        // Registered before opening: a removal while the device is being opened is still seen.
        val removal =
            object : MidiManager.DeviceCallback() {
                override fun onDeviceRemoved(info: MidiDeviceInfo) {
                    if (info.id == device.systemId) onClosed()
                }
            }
        register(manager, removal)
        return openPort(manager, device) { it.openOutputPort(0) }
            ?.let { (opened, port) ->
                port.connect(receiver)
                AutoCloseable {
                    manager.unregisterDeviceCallback(removal)
                    port.disconnect(receiver)
                    port.close()
                    opened.close()
                }
            }.also { if (it == null) manager.unregisterDeviceCallback(removal) }
    }

    override suspend fun openOutput(device: SystemDevice): SystemSender? {
        val manager = manager ?: return null
        return openPort(manager, device) { it.openInputPort(0) }?.let { (opened, port) ->
            object : SystemSender {
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
    }

    override fun bluetoothProblem(): Reason? =
        when {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).any {
                context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            } -> {
                Reason.PERMISSION_DENIED
            }

            manager == null || bluetooth?.isEnabled != true || bluetooth.bluetoothLeScanner == null -> {
                Reason.BLUETOOTH_OFF
            }

            else -> {
                null
            }
        }

    // Callers check bluetoothProblem() first (permissions granted); a race still ends in SecurityException.
    @SuppressLint("MissingPermission")
    override fun scanBluetooth(): Flow<BluetoothAdvertisement> =
        callbackFlow {
            val scanner = bluetooth?.bluetoothLeScanner ?: throw BluetoothUnavailableException(Reason.BLUETOOTH_OFF)
            val callback =
                object : ScanCallback() {
                    override fun onScanResult(
                        callbackType: Int,
                        result: ScanResult,
                    ) {
                        trySend(BluetoothAdvertisement(result.device.address, result.scanRecord?.deviceName))
                    }

                    override fun onBatchScanResults(results: List<ScanResult>) = results.forEach { onScanResult(0, it) }

                    override fun onScanFailed(errorCode: Int) {
                        close(BluetoothUnavailableException(Reason.BLUETOOTH_OFF))
                    }
                }
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BLE_MIDI_SERVICE_UUID)).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            try {
                scanner.startScan(listOf(filter), settings, callback)
            } catch (e: SecurityException) {
                throw BluetoothUnavailableException(Reason.PERMISSION_DENIED).apply { initCause(e) }
            } catch (e: IllegalStateException) {
                // Bluetooth was switched off just now
                throw BluetoothUnavailableException(Reason.BLUETOOTH_OFF).apply { initCause(e) }
            }
            awaitClose { if (bluetooth.isEnabled) scanner.stopScan(callback) }
        }

    // Called only when bluetoothProblem() is null (permissions granted).
    @SuppressLint("MissingPermission")
    override suspend fun openBluetooth(address: String): AutoCloseable? {
        val manager = manager
        val device = bluetooth?.takeIf { BluetoothAdapter.checkBluetoothAddress(address) }?.getRemoteDevice(address)
        if (manager == null || device == null) return null
        return suspendCancellableCoroutine<MidiDevice?> { continuation ->
            manager.openBluetoothDevice(
                device,
                { opened -> continuation.resume(opened) { _, value, _ -> value?.close() } },
                handler,
            )
        }
    }

    private fun refresh(manager: MidiManager) =
        synchronized(list) { list.value = currentInfos(manager).map(::toSystemDevice) }

    /** Opens [device] and one of its ports with [openPort]; null, with nothing left open, if either fails. */
    private suspend fun <P : Any> openPort(
        manager: MidiManager,
        device: SystemDevice,
        openPort: (MidiDevice) -> P?,
    ): Pair<MidiDevice, P>? {
        val opened = open(manager, device) ?: return null
        val port = runCatching { openPort(opened) }.onFailure { opened.close() }.getOrThrow()
        if (port == null) opened.close()
        return port?.let { opened to it }
    }

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
}

@ExcludeFromCoverage(GLUE)
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

@ExcludeFromCoverage(GLUE)
private fun bluetoothDevice(properties: Bundle): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        properties.getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        properties.getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE) as? BluetoothDevice
    }
