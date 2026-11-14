package pianoanchor.core.model

/** Thrown by a storage port when the store can't be read or written (disk full, corrupt or locked file). */
public class StorageUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
