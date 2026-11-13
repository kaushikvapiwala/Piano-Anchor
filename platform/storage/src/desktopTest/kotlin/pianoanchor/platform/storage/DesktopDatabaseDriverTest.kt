package pianoanchor.platform.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Instrument
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Instant

class DesktopDatabaseDriverTest {
    private val directory: File = createTempDirectory("piano-anchor-db").toFile()
    private val file = File(directory, "nested/piano-anchor.db")

    @AfterTest
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun dataSurvivesReopeningTheFile() =
        runTest {
            val seen = Instant.fromEpochMilliseconds(1_000)
            val piano = Instrument("usb-1", "Piano", identity = null, firstSeen = seen, lastSeen = seen)
            createPianoAnchorDatabaseDriver(file).use { driver ->
                SqlInstrumentRepositoryAdapter(driver, Dispatchers.Unconfined).upsert(piano)
            }
            createPianoAnchorDatabaseDriver(file).use { driver ->
                assertEquals(piano, SqlInstrumentRepositoryAdapter(driver, Dispatchers.Unconfined).get("usb-1"))
            }
        }

    @Test
    fun foreignKeysAreOn() {
        createPianoAnchorDatabaseDriver(file).use { driver ->
            assertFails {
                driver.exec(
                    "INSERT INTO attempt_event(attempt_id, seq, kind, t_nanos, step_index) " +
                        "VALUES ('none', 0, 'step_correct', 0, 0)",
                )
            }
        }
    }
}
