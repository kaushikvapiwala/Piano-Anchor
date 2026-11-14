package pianoanchor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class StorageUnavailableExceptionTest {
    @Test
    fun keepsTheMessageAndTheDriverCause() {
        val cause = IllegalStateException("disk full")
        val e = StorageUnavailableException("save failed", cause)
        assertEquals("save failed", e.message)
        assertSame(cause, e.cause)
    }

    @Test
    fun theCauseIsOptional() {
        assertNull(StorageUnavailableException("get failed").cause)
    }
}
