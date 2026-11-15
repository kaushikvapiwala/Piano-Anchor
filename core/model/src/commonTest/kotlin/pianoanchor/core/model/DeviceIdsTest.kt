package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceIdsTest {
    @Test
    fun usbAndBluetoothIdsAreStable() {
        assertTrue(isStableDeviceId("usb:Roland/FP-30/123"))
        assertTrue(isStableDeviceId("ble:AA:BB"))
    }

    @Test
    fun systemIdsAreUnstable() {
        assertFalse(isStableDeviceId("sys:7"))
    }

    @Test
    fun anIdThatOnlyContainsThePrefixLaterIsStable() {
        assertTrue(isStableDeviceId("usb:a/sys:/1"))
    }

    @Test
    fun theUnstablePrefixIsSys() {
        assertEquals("sys:", UNSTABLE_DEVICE_ID_PREFIX)
    }
}
