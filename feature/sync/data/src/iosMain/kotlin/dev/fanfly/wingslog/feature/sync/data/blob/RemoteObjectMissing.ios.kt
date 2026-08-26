package dev.fanfly.wingslog.feature.sync.data.blob

/**
 * `FIRStorageErrorCodeObjectNotFound` is -13010, the same code Android reports.
 *
 * Matched on the message rather than the typed error: GitLive surfaces iOS Storage failures as a
 * plain exception carrying the NSError's description, so the code is not reachable as a field here.
 * Kept narrow — both the numeric code and Apple's wording have to appear — and it fails **closed**:
 * an unrecognised error stays transient and is retried, which is the pre-#426 behaviour rather than
 * a new way to discard a blob.
 */
internal actual fun Throwable.isRemoteObjectMissing(): Boolean =
  generateSequence(this) { it.cause }
    .mapNotNull { it.message }
    .any { message ->
      message.contains("-13010") || message.contains("does not exist", ignoreCase = true)
    }
