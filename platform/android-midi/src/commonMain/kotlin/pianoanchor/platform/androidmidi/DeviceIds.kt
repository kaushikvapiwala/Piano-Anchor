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
 * `usb:<manufacturer>/<product>/<serial>`, `ble:<ADDRESS>`, else `sys:<systemId>`. A device whose id is already
 * taken in [devices] gets `#<systemId>` appended. Ids are computed over the whole list, so input and output
 * listings agree.
 */
internal fun identify(devices: List<SystemDevice>): List<ListedDevice> {
    val taken = mutableSetOf<String>()
    return devices.map { system ->
        val base = baseId(system)
        val id = if (taken.add(base)) base else "$base#${system.systemId}".also { taken.add(it) }
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
