package pianoanchor.platform.storage

import android.database.SQLException
import android.util.Log
import pianoanchor.core.model.ExcludeFromCoverage
import pianoanchor.core.model.StorageUnavailableException

private const val GLUE = "android.database glue; checked on device"

/** Logcat tag of the storage glue (docs/debugging.md). */
private const val TAG = "PianoAnchorDb"

// SQLiteFullException, SQLiteDatabaseCorruptException, SQLiteDatabaseLockedException etc. are all SQLExceptions.
@ExcludeFromCoverage(GLUE)
internal actual fun Throwable.isStorageFailure(): Boolean = this is SQLException

@ExcludeFromCoverage(GLUE)
internal actual fun logStorageFailure(failure: StorageUnavailableException) {
    Log.w(TAG, failure.message, failure.cause)
}
