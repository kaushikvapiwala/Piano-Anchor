package pianoanchor.core.model

/** Prefix of a device id built from Android's per-connection id: it changes on reconnect and may be reused. */
public const val UNSTABLE_DEVICE_ID_PREFIX: String = "sys:"

/** True unless [deviceId] starts with [UNSTABLE_DEVICE_ID_PREFIX]. */
public fun isStableDeviceId(deviceId: String): Boolean = !deviceId.startsWith(UNSTABLE_DEVICE_ID_PREFIX)
