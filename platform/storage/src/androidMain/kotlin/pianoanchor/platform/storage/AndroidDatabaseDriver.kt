package pianoanchor.platform.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import pianoanchor.core.model.ExcludeFromCoverage
import pianoanchor.platform.storage.db.PianoAnchorDatabase

/** File name of the app's one database. */
private const val DATABASE_NAME = "piano-anchor.db"

/**
 * Opens the app's database ([DATABASE_NAME] in the app's database directory), creating or migrating it to the current
 * schema, with foreign keys on. The file opens lazily, on the first query.
 */
@ExcludeFromCoverage("Android SQLite needs a device; the adapters are tested on the JVM driver, this by the app")
public fun createPianoAnchorDatabaseDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(
        schema = PianoAnchorDatabase.Schema,
        context = context,
        name = DATABASE_NAME,
        callback =
            object : AndroidSqliteDriver.Callback(PianoAnchorDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
    )
