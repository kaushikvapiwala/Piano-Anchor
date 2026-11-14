package pianoanchor.platform.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import pianoanchor.core.model.StorageUnavailableException

/** Whether this is the platform driver's own database exception (disk full, corrupt or locked file, …). */
internal expect fun Throwable.isStorageFailure(): Boolean

/**
 * Runs [block], rethrowing a driver failure as [StorageUnavailableException] ("[operation] failed") and anything else
 * (cancellation, the duplicate-id [IllegalArgumentException], bugs) unchanged. Common code can't name the platform's
 * SQLException, so it catches everything and rethrows what isn't one.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T> storage(
    operation: String,
    block: () -> T,
): T =
    try {
        block()
    } catch (e: Exception) {
        throw e.asStorageUnavailable(operation)
    }

/** [this] flow, failing with [StorageUnavailableException] instead of a driver failure, as [storage] does. */
internal fun <T> Flow<T>.storage(operation: String): Flow<T> = catch { throw it.asStorageUnavailable(operation) }

private fun Throwable.asStorageUnavailable(operation: String): Throwable =
    if (isStorageFailure()) StorageUnavailableException("$operation failed", this) else this
