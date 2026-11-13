package pianoanchor.core.model

import pianoanchor.core.model.BluetoothUnavailableException.Reason
import kotlin.test.Test
import kotlin.test.assertEquals

class BluetoothUnavailableExceptionTest {
    @Test
    fun carriesTheReasonInFieldAndMessage() {
        val e = BluetoothUnavailableException(Reason.PERMISSION_DENIED)
        assertEquals(Reason.PERMISSION_DENIED, e.reason)
        assertEquals("Bluetooth unavailable: PERMISSION_DENIED", e.message)
    }
}
