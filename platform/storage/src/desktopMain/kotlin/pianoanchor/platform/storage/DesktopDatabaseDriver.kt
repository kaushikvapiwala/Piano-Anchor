package pianoanchor.platform.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import pianoanchor.platform.storage.db.PianoAnchorDatabase
import java.io.File
import java.util.Properties

/**
 * Opens the database in [file] on the JVM, creating it (and its parent directories) or migrating it to the current
 * schema, with foreign keys on. Close the driver when done.
 */
public fun createPianoAnchorDatabaseDriver(file: File): SqlDriver {
    file.absoluteFile.parentFile.mkdirs()
    return jdbcDatabaseDriver("jdbc:sqlite:${file.path}")
}

/** The database at a JDBC SQLite [url] ([JdbcSqliteDriver.IN_MEMORY] in tests), schema current, foreign keys on. */
internal fun jdbcDatabaseDriver(url: String): SqlDriver =
    JdbcSqliteDriver(url, Properties().apply { setProperty("foreign_keys", "true") }, PianoAnchorDatabase.Schema)
