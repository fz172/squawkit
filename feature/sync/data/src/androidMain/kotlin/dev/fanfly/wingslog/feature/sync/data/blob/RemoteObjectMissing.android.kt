package dev.fanfly.wingslog.feature.sync.data.blob

import com.google.firebase.storage.StorageException

/** `ERROR_OBJECT_NOT_FOUND` is Storage's 404 (`HttpResult: 404`, code -13010). */
internal actual fun Throwable.isRemoteObjectMissing(): Boolean =
  generateSequence(this) { it.cause }
    .any { it is StorageException && it.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND }
