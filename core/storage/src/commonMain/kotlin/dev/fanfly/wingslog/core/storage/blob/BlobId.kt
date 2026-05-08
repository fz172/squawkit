package dev.fanfly.wingslog.core.storage.blob

import kotlin.jvm.JvmInline

/**
 * Globally-unique identifier for an attachment binary. Same value as the `Attachment.id` field
 * inside the owning entity's proto, so the proto and the local [blob_object] table join on it
 * directly.
 *
 * Wrapped in a value class so a `BlobId` can never be confused with an arbitrary `String`.
 */
@JvmInline
value class BlobId(val value: String) {
  init {
    require(value.isNotBlank()) { "BlobId must not be blank" }
  }
}
