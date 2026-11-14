package pianoanchor.platform.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import pianoanchor.core.model.Band
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.PassageComplete
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun migrationFromVersion1KeepsOldAttemptsWithNullTempoAndNoNotes() =
        runTest {
            file.parentFile.mkdirs()
            // The committed version-1 schema file, with an attempt and an event written the way version 1 did.
            File("src/commonMain/sqldelight/databases/1.db").copyTo(file)
            JdbcSqliteDriver("jdbc:sqlite:${file.path}").use { old ->
                old.exec("PRAGMA user_version = 1")
                old.exec(
                    "INSERT INTO attempt VALUES ('old-1', 'builtin:cde', 'usb-1', 1234, 5000, 3, 0, 0, 1.0, 'CLEAN')",
                )
                old.exec("INSERT INTO attempt_event VALUES ('old-1', 0, 'passage_complete', 0, 2, NULL, NULL)")
            }

            createPianoAnchorDatabaseDriver(file).use { driver ->
                val repository = SqlAttemptRepositoryAdapter(driver, Dispatchers.Unconfined)
                val old = assertNotNull(repository.get("old-1"))
                assertNull(old.tempoBpm)
                assertEquals(emptyList(), old.playedNotes)
                assertEquals(listOf(PassageComplete(0, 2)), old.events)
                assertEquals(Band.CLEAN, old.band)
                assertEquals(emptySet(), repository.cleanTempos("builtin:cde"))
            }
        }
}
