package pianoanchor.platform.androidmidi

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceIdsTest {
    private fun idOf(device: SystemDevice) = identify(listOf(device)).single().device.id

    private fun nameOf(device: SystemDevice) = identify(listOf(device)).single().device.name

    private val bluetooth =
        SystemDevice(systemId = 7, transport = Transport.BLUETOOTH, inputPorts = 1, outputPorts = 1)

    @Test
    fun usbWithAllPropertiesUsesManufacturerProductAndSerial() {
        assertEquals("usb:Acme/Stage Piano/SN1", idOf(PIANO))
    }

    @Test
    fun usbWithoutSerialFallsBackToTheSystemId() {
        assertEquals("sys:1", idOf(PIANO.copy(serial = null)))
    }

    @Test
    fun usbWithoutManufacturerOrProductFallsBackToTheSystemId() {
        assertEquals("sys:1", idOf(PIANO.copy(manufacturer = null)))
        assertEquals("sys:1", idOf(PIANO.copy(product = " ")))
    }

    @Test
    fun bluetoothUsesTheUpperCasedAddress() {
        assertEquals("ble:AA:BB:CC:DD:EE:0F", idOf(bluetooth.copy(bluetoothAddress = "aa:bb:cc:dd:ee:0f")))
    }

    @Test
    fun bluetoothWithoutAddressFallsBackToTheSystemId() {
        assertEquals("sys:7", idOf(bluetooth))
    }

    @Test
    fun otherTransportsUseTheSystemIdEvenWithUsbProperties() {
        assertEquals("sys:1", idOf(PIANO.copy(transport = Transport.OTHER, bluetoothAddress = "AA")))
    }

    @Test
    fun duplicateIdsGetTheSystemIdAppended() {
        val twin = PIANO.copy(systemId = 2)
        val ids = identify(listOf(PIANO, twin, PIANO.copy(systemId = 3))).map { it.device.id }
        assertEquals(
            listOf("usb:Acme/Stage Piano/SN1", "usb:Acme/Stage Piano/SN1#2", "usb:Acme/Stage Piano/SN1#3"),
            ids,
        )
    }

    @Test
    fun theEarlierConnectionKeepsTheBareIdWhateverTheListOrder() {
        val ids = identify(listOf(PIANO.copy(systemId = 9), PIANO)).associate { it.system.systemId to it.device.id }
        assertEquals(mapOf(1 to "usb:Acme/Stage Piano/SN1", 9 to "usb:Acme/Stage Piano/SN1#9"), ids)
    }

    @Test
    fun aSuffixedIdNeverCollidesWithARealId() {
        val serialLooksSuffixed = PIANO.copy(systemId = 1, serial = "SN1#2")
        val first = PIANO.copy(systemId = 0)
        val second = PIANO.copy(systemId = 2)
        val ids = identify(listOf(serialLooksSuffixed, first, second)).map { it.device.id }
        assertEquals(ids.toSet().size, ids.size, "ids must be unique: $ids")
        assertEquals("usb:Acme/Stage Piano/SN1#2#2", ids[2])
    }

    @Test
    fun nameFallsBackToProductThenToAGenericName() {
        assertEquals("Acme Stage Piano", nameOf(PIANO))
        assertEquals("Stage Piano", nameOf(PIANO.copy(name = "")))
        assertEquals(FALLBACK_DEVICE_NAME, nameOf(PIANO.copy(name = null, product = null)))
    }
}
