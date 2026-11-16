package pianoanchor.platform.storage

import pianoanchor.core.model.StorageUnavailableException
import java.sql.SQLException

internal actual fun Throwable.isStorageFailure(): Boolean = this is SQLException

/** The desktop app is a developer tool: the failure already reaches its screen, so nothing more is recorded. */
internal actual fun logStorageFailure(failure: StorageUnavailableException): Unit = Unit
