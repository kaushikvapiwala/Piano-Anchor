package pianoanchor.platform.storage

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pianoanchor.core.ports.CalibrationRepositoryPort
import pianoanchor.platform.storage.db.PianoAnchorDatabase

/**
 * [CalibrationRepositoryPort] in the on-device SQLite database. [driver] comes from `createPianoAnchorDatabaseDriver`
 * (schema created, foreign keys on); every query runs on [dispatcher] (`Dispatchers.IO` in the app).
 */
public class SqlCalibrationRepositoryAdapter(
    driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher,
) : CalibrationRepositoryPort {
    private val queries = PianoAnchorDatabase(driver).calibrationQueries

    override suspend fun latencyOffsetNanos(deviceId: String): Long? =
        withContext(dispatcher) {
            storage("latencyOffsetNanos") { queries.selectOffset(deviceId).executeAsOneOrNull() }
        }

    override suspend fun setLatencyOffsetNanos(
        deviceId: String,
        offsetNanos: Long,
    ): Unit =
        withContext(dispatcher) {
            storage("setLatencyOffsetNanos") { queries.setOffset(deviceId, offsetNanos) }
        }
}
