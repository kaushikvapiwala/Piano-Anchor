package pianoanchor.platform.storage

import java.sql.SQLException

internal actual fun Throwable.isStorageFailure(): Boolean = this is SQLException
