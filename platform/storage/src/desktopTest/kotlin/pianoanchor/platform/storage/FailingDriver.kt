package pianoanchor.platform.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import java.sql.SQLException

/** [delegate], except that every statement throws [SQLException] while [failing], as a full or locked database does. */
internal class FailingDriver(
    private val delegate: SqlDriver,
) : SqlDriver by delegate {
    var failing = false

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        failIfFailing()
        return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
    }

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        failIfFailing()
        return delegate.execute(identifier, sql, parameters, binders)
    }

    private fun failIfFailing() {
        if (failing) throw SQLException("database or disk is full")
    }
}
