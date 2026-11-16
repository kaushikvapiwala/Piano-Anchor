package pianoanchor.platform.storage

import android.database.SQLException
import android.util.Log
import pianoanchor.core.model.ExcludeFromCoverage

/** Logcat tag of the storage glue (docs/debugging.md). */
private const val TAG = "PianoAnchorDb"

// SQLiteFullException, SQLiteDatabaseCorruptException, SQLiteDatabaseLockedException etc. are all SQLExceptions.
// Logged here because callers turn them into StorageUnavailableException, which the screens catch and only show.
@ExcludeFromCoverage("android.database glue; checked on device")
internal actual fun Throwable.isStorageFailure(): Boolean =
    (this is SQLException).also { if (it) Log.w(TAG, "database failure", this) }
