package pianoanchor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ReleaseVersionTest {
    @Test
    fun `first release has code 100`() = assertEquals(100, releaseVersionCode("0.1.0"))

    @Test
    fun `major minor and patch are weighted`() = assertEquals(10203, releaseVersionCode("1.2.3"))

    @Test
    fun `largest minor and patch still fit`() = assertEquals(19999, releaseVersionCode("1.99.99"))

    @Test
    fun `rejects minor 100`() {
        assertThrows(IllegalArgumentException::class.java) { releaseVersionCode("1.100.0") }
    }

    @Test
    fun `rejects patch 100`() {
        assertThrows(IllegalArgumentException::class.java) { releaseVersionCode("1.0.100") }
    }

    @Test
    fun `rejects leading zeros`() {
        assertThrows(IllegalArgumentException::class.java) { releaseVersionCode("0.01.0") }
    }

    @Test
    fun `rejects a name that is not X_Y_Z`() {
        assertThrows(IllegalArgumentException::class.java) { releaseVersionCode("0.0.0-dev") }
    }
}
