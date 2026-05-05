package dev.fanfly.wingslog.feature.attachment.datamanager

/**
 * Platform-abstract handle on the per-app private directory where blob bytes live. The
 * [SqlDelightLocalBlobStore] consults this for actual disk I/O — the database row tracks "is the
 * file present?" via state, but the bytes live on disk.
 *
 * See docs/storage_r2_design.md §6.1 ("`BlobFilesystem.kt` expect — filesDir, read/write/delete").
 *
 * In production:
 * - Android: rooted at `Context.filesDir/blobs/`.
 * - iOS: rooted at `NSDocumentDirectory/blobs/` with `NSURLIsExcludedFromBackupKey` set on the
 *   directory so iCloud doesn't replicate attachments.
 *
 * Modelled as an interface (rather than `expect class`) so JVM unit tests can pass an instance
 * scoped to a `@TempDir` directly, without per-platform actuals leaking into the test classpath.
 */
interface BlobFilesystem {

  /**
   * `relativePath` is e.g. `"blobs/{id}.bin"` — the same value persisted in
   * `blob_object.relative_path`. Implementations resolve it against the platform root.
   */
  suspend fun write(relativePath: String, bytes: ByteArray)

  suspend fun read(relativePath: String): ByteArray

  suspend fun delete(relativePath: String)

  suspend fun exists(relativePath: String): Boolean

  /** Returns the `file://`-scheme URI suitable for handing to a system viewer / share intent. */
  fun uriFor(relativePath: String): String
}

/** Canonical relative path for a blob id. Both the production code and tests must agree. */
internal fun blobRelativePath(id: String): String = "blobs/$id.bin"
