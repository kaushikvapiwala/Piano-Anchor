package pianoanchor.testing

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Attempt
import pianoanchor.core.model.Band
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.StorageUnavailableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class FakeRepositoryFailureTest {
    private val at = Instant.fromEpochMilliseconds(1_000)
    private val attempt =
        Attempt(
            id = "a-1",
            passageId = "c-d-e",
            deviceId = "usb-1",
            startedAt = at,
            durationNanos = 0,
            stepCount = 3,
            wrongNotes = 0,
            incompleteChords = 0,
            accuracy = 1.0,
            band = Band.CLEAN,
            events = emptyList(),
            tempoBpm = null,
            playedNotes = emptyList(),
        )
    private val instrument = Instrument("usb-1", "Piano", identity = null, firstSeen = at, lastSeen = at)

    @Test
    fun aFailingAttemptRepositoryThrowsStorageUnavailableExceptionFromEveryMethod() =
        runTest {
            val attempts = FakeAttemptRepository().apply { failing = true }

            assertFailsWith<StorageUnavailableException> { attempts.save(attempt) }
            assertFailsWith<StorageUnavailableException> { attempts.get("a-1") }
            assertFailsWith<StorageUnavailableException> { attempts.countStartedBefore("c-d-e", at) }
            assertFailsWith<StorageUnavailableException> { attempts.cleanTempos("c-d-e") }
        }

    @Test
    fun recentOfAFailingAttemptRepositoryFailsOnCollection() =
        runTest {
            val attempts = FakeAttemptRepository()
            val recent = attempts.recent("c-d-e", 1) // creating the flow does not fail
            attempts.failing = true

            assertFailsWith<StorageUnavailableException> { recent.first() }
        }

    @Test
    fun anAttemptRepositoryWorksAgainOnceItStopsFailing() =
        runTest {
            val attempts = FakeAttemptRepository().apply { failing = true }
            assertFailsWith<StorageUnavailableException> { attempts.save(attempt) }

            attempts.failing = false
            attempts.save(attempt)

            assertEquals(attempt, attempts.get("a-1"))
        }

    @Test
    fun aFailingInstrumentRepositoryThrowsStorageUnavailableExceptionFromEveryMethod() =
        runTest {
            val instruments = FakeInstrumentRepository().apply { failing = true }

            assertFailsWith<StorageUnavailableException> { instruments.upsert(instrument) }
            assertFailsWith<StorageUnavailableException> { instruments.get("usb-1") }
        }
}
