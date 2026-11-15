package pianoanchor.engine.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionCompareTest {
    @Test
    fun `newer patch, minor, major`() {
        assertEquals(true, isNewer("0.0.2", "0.0.1"))
        assertEquals(true, isNewer("0.1.0", "0.0.9"))
        assertEquals(true, isNewer("1.0.0", "0.9.9"))
        assertEquals(true, isNewer("0.10.0", "0.9.0"))
    }

    @Test
    fun `older is not newer`() {
        assertEquals(false, isNewer("0.0.1", "0.0.2"))
        assertEquals(false, isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun `equal is not newer`() {
        assertEquals(false, isNewer("1.2.3", "1.2.3"))
    }

    @Test
    fun `malformed gives null`() {
        for (bad in listOf("", "1.2", "1.2.3.4", "1.2.x", "1.2.3-beta", "vv1.2.3", "99999999999.0.0")) {
            assertNull(isNewer(bad, "1.0.0"), bad)
            assertNull(isNewer("1.0.0", bad), bad)
        }
    }

    @Test
    fun `v prefix ok`() {
        assertEquals(true, isNewer("v0.2.0", "0.1.0"))
        assertEquals(false, isNewer("0.1.0", "v0.1.0"))
    }
}
