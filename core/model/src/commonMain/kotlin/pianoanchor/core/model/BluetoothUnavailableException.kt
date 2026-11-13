package pianoanchor.core.model

/** Thrown when Bluetooth LE scanning or connecting is not possible right now; [reason] says why. */
public class BluetoothUnavailableException(
    public val reason: Reason,
) : Exception("Bluetooth unavailable: $reason") {
    /** Why Bluetooth LE can't be used. */
    public enum class Reason {
        /** The app lacks the permission to scan for or connect to Bluetooth devices. */
        PERMISSION_DENIED,

        /** Bluetooth is switched off, or the device has no Bluetooth LE. */
        BLUETOOTH_OFF,
    }
}
