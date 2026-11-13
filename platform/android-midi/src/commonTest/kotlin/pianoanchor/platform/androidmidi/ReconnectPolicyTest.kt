package pianoanchor.platform.androidmidi

import pianoanchor.engine.Tunables
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReconnectPolicyTest {
    private fun ReconnectPolicy.allDelays(): List<Long> = generateSequence { nextDelayMs() }.toList()

    @Test
    fun delaysDoubleFromOneSecondAndAreCappedAtThirtySeconds() {
        val delays = ReconnectPolicy().allDelays()
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), delays.take(7))
    }

    @Test
    fun givesUpOnceTheNextAttemptWouldPassTheGiveUpTime() {
        val policy = ReconnectPolicy()
        val delays = policy.allDelays()
        assertEquals(List(8) { 30_000L }, delays.drop(5))
        assertEquals(271_000L, delays.sum())
        assertTrue(delays.sum() + Tunables.BLE_RECONNECT_MAX_MS > Tunables.BLE_RECONNECT_GIVE_UP_MS)
        assertNull(policy.nextDelayMs())
    }

    @Test
    fun resetStartsOverAfterASuccess() {
        val policy = ReconnectPolicy()
        repeat(3) { policy.nextDelayMs() }
        policy.reset()
        assertEquals(ReconnectPolicy().allDelays(), policy.allDelays())
    }
}
