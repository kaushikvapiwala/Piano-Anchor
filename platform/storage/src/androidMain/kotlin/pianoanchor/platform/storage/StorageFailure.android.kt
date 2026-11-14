package pianoanchor.platform.storage

import android.database.SQLException
import pianoanchor.core.model.ExcludeFromCoverage

// SQLiteFullException, SQLiteDatabaseCorruptException, SQLiteDatabaseLockedException etc. are all SQLExceptions.
@ExcludeFromCoverage("android.database glue; checked on device")
internal actual fun Throwable.isStorageFailure(): Boolean = this is SQLException
