package pianoanchor.platform.storage

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pianoanchor.core.model.Instrument
import pianoanchor.core.model.InstrumentIdentity
import pianoanchor.core.ports.InstrumentRepositoryPort
import pianoanchor.platform.storage.db.PianoAnchorDatabase
import kotlin.time.Instant

/**
 * [InstrumentRepositoryPort] in the on-device SQLite database. [driver] comes from `createPianoAnchorDatabaseDriver`
 * (schema created, foreign keys on); every query runs on [dispatcher] (`Dispatchers.IO` in the app). Instants are
 * stored as epoch milliseconds, so anything finer is dropped.
 */
public class SqlInstrumentRepositoryAdapter(
    driver: SqlDriver,
    private val dispatcher: CoroutineDispatcher,
) : InstrumentRepositoryPort {
    private val queries = PianoAnchorDatabase(driver).instrumentQueries

    override suspend fun upsert(instrument: Instrument): Unit =
        withContext(dispatcher) {
            storage("upsert") {
                val identity = instrument.identity
                queries.upsert(
                    device_id = instrument.deviceId,
                    name = instrument.name,
                    mfr_id = identity?.let { it.manufacturerId.toHex() },
                    family = identity?.family?.toLong(),
                    model = identity?.model?.toLong(),
                    version = identity?.let { it.version.toHex() },
                    first_seen_ms = instrument.firstSeen.toEpochMilliseconds(),
                    last_seen_ms = instrument.lastSeen.toEpochMilliseconds(),
                )
            }
        }

    override suspend fun get(deviceId: String): Instrument? =
        withContext(dispatcher) {
            storage("get") { queries.selectByDeviceId(deviceId, ::toInstrument).executeAsOneOrNull() }
        }
}

// The table's CHECK makes the identity columns all null or all set, so the non-null assertions cannot fail.
@Suppress("LongParameterList")
private fun toInstrument(
    deviceId: String,
    name: String,
    mfrId: String?,
    family: Long?,
    model: Long?,
    version: String?,
    firstSeenMs: Long,
    lastSeenMs: Long,
) = Instrument(
    deviceId = deviceId,
    name = name,
    identity =
        mfrId?.let {
            InstrumentIdentity(it.hexToBytes(), family!!.toInt(), model!!.toInt(), version!!.hexToBytes())
        },
    firstSeen = Instant.fromEpochMilliseconds(firstSeenMs),
    lastSeen = Instant.fromEpochMilliseconds(lastSeenMs),
)
