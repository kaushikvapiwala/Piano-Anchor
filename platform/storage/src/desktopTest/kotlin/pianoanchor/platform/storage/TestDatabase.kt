package pianoanchor.platform.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

/** A fresh in-memory database with the current schema and foreign keys on. */
internal fun inMemoryDriver(): SqlDriver = jdbcDatabaseDriver(JdbcSqliteDriver.IN_MEMORY)

/** Runs raw [sql] (no parameters), bypassing the adapters. */
internal fun SqlDriver.exec(sql: String) {
    execute(identifier = null, sql = sql, parameters = 0)
}

/** The number of rows in [table]. */
internal fun SqlDriver.count(table: String): Long =
    executeQuery(
        identifier = null,
        sql = "SELECT count(*) FROM $table",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(checkNotNull(cursor.getLong(0)))
        },
        parameters = 0,
    ).value
