package pianoanchor.engine

import kotlin.test.Test
import kotlin.test.assertTrue

class TunablesTest {
    @Test
    fun monitorGiveUpOutlastsTheBleAdapterGiveUp() {
        assertTrue(
            Tunables.MONITOR_RECONNECT_GIVE_UP_MS >= Tunables.BLE_RECONNECT_GIVE_UP_MS + Tunables.BLE_RECONNECT_MAX_MS,
        )
    }
}
