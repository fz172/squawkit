package dev.fanfly.wingslog.feature.sync.data.blob

/**
 * The Firebase JS SDK reports this as the error code `storage/object-not-found`.
 *
 * Like the iOS actual this reads the message, because GitLive does not surface the JS error object
 * here, and it fails **closed** — an unrecognised error stays transient and is retried.
 */
internal actual fun Throwable.isRemoteObjectMissing(): Boolean =
  generateSequence(this) { it.cause }
    .mapNotNull { it.message }
    .any { it.contains("storage/object-not-found", ignoreCase = true) }
