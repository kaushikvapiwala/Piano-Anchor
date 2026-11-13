package pianoanchor.platform.androidmidi

import pianoanchor.core.model.MidiDevice

/** Display name when Android reports neither a name nor a product (UI string resource comes with the monitor). */
internal const val FALLBACK_DEVICE_NAME = "MIDI device"

/** A platform device with the [MidiDevice] the ports list it as. */
internal data class ListedDevice(
    val system: SystemDevice,
    val device: MidiDevice,
)

/**
 * Gives each platform device a stable id (see `docs/protocols/android-midi.md`):
 * `usb:<manufacturer>/<product>/<serial>`, `ble:<ADDRESS>`, else `sys:<systemId>`. Devices are taken in
 * [SystemDevice.systemId] order (Android numbers connections in order, so the earlier one keeps its id) and a device
 * whose id is already taken gets `#<systemId>` appended until it is unique. Ids are computed over the whole list,
 * so input and output listings agree. The result is in [SystemDevice.systemId] order.
 */
internal fun identify(devices: List<SystemDevice>): List<ListedDevice> {
    val taken = mutableSetOf<String>()
    return devices.sortedBy { it.systemId }.map { system ->
        var id = baseId(system)
        while (!taken.add(id)) id = "$id#${system.systemId}"
        ListedDevice(system, MidiDevice(id = id, name = displayName(system)))
    }
}

private fun baseId(device: SystemDevice): String {
    val manufacturer = device.manufacturer.present()
    val product = device.product.present()
    val serial = device.serial.present()
    val address = device.bluetoothAddress.present()
    return when {
        device.transport == Transport.USB && manufacturer != null && product != null && serial != null -> {
            "usb:$manufacturer/$product/$serial"
        }

        device.transport == Transport.BLUETOOTH && address != null -> {
            "ble:${address.uppercase()}"
        }

        else -> {
            "sys:${device.systemId}"
        }
    }
}

private fun displayName(device: SystemDevice): String =
    device.name.present() ?: device.product.present() ?: FALLBACK_DEVICE_NAME

private fun String?.present(): String? = this?.takeIf { it.isNotBlank() }
