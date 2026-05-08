package dev.fanfly.wingslog.feature.attachment.datamanager

import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.blob.BlobId
import kotlinx.coroutines.flow.Flow

/**
 * Reactive, device-local store for attachment binaries. Mirrors the
 * `dev.fanfly.wingslog.core.storage.EntityStore` shape but for `blob_object` rows + filesystem
 * bytes. The only path that writes the `blob_object` table.
 *
 * See docs/storage_r2_design.md §6.1.
 *
 * State machine (§5):
 *
 * ```
 *   put(...) ─────────────► LOCAL_ONLY ──markUploading──► UPLOADING
 *                              ▲                            │
 *                              │ markFailedTransient        │ markUploaded
 *                              │ markFailedPermanent        ▼
 *                              └──────────────────────── SYNCED
 *
 *   upsertRemoteOnly(...) ──► REMOTE_ONLY ──installDownloaded──► SYNCED
 * ```
 */
interface LocalBlobStore {

  /**
   * Write [bytes] to `{filesDir}/blobs/{id}.bin`, compute sha256, and insert/replace the
   * `blob_object` row in `LOCAL_ONLY` state. Returns the row that was just written so the caller
   * can grab the sha256 (e.g. to populate the proto's `Attachment.sha256`).
   */
  suspend fun put(
    id: BlobId,
    bytes: ByteArray,
    contentType: String?,
    scope: EntityScope,
  ): BlobRef

  /**
   * Insert a placeholder row in `REMOTE_ONLY` state — no local file. Driven by
   * [BlobIndexReconciler] when a pulled proto references an attachment id we don't yet have.
   * Idempotent: a second call with the same id is a no-op (does not overwrite a row that has
   * progressed past `REMOTE_ONLY`).
   */
  suspend fun upsertRemoteOnly(
    id: BlobId,
    sha256: String,
    sizeBytes: Long,
    contentType: String?,
    scope: EntityScope,
  )

  fun observe(id: BlobId): Flow<BlobRef?>

  suspend fun get(id: BlobId): BlobRef?

  /** `file://` URI to the local copy if it exists (LOCAL_ONLY/UPLOADING/SYNCED), else null. */
  suspend fun localUri(id: BlobId): String?

  /** LOCAL_ONLY → UPLOADING. Bumps `last_attempt_at` to now. */
  suspend fun markUploading(id: BlobId)

  /** UPLOADING → SYNCED. Stamps `remote_path` and resets `upload_attempts` to 0. */
  suspend fun markUploaded(id: BlobId, remotePath: String)

  /**
   * UPLOADING → LOCAL_ONLY (reverts so the scheduler picks it up again).
   * Increments `upload_attempts`, stamps `last_attempt_at`.
   */
  suspend fun markFailedTransient(id: BlobId)

  /**
   * Permanent upload error (auth lost, file missing, 403). UPLOADING → LOCAL_ONLY but the row
   * is left at-rest until the user manually retries (per design §5.1; we don't introduce a
   * dedicated FAILED state). [cause] is recorded via [Logger]; UI surfaces it via
   * `AttachmentManager.observeStatus(id)` reading the upload_attempts counter.
   */
  suspend fun markFailedPermanent(id: BlobId, cause: Throwable)

  /**
   * Verify [bytes]'s sha256 matches [expectedSha256], write to disk, flip REMOTE_ONLY → SYNCED.
   * On mismatch returns [Result.failure] with [IntegrityError] and writes nothing — the row stays
   * REMOTE_ONLY so the caller can retry.
   */
  suspend fun installDownloaded(
    id: BlobId,
    bytes: ByteArray,
    expectedSha256: String,
  ): Result<Unit>

  /**
   * Tombstone the row (`deleted=1`) and remove the local file. Cleanup of the gs:// remote object
   * is the caller's responsibility (driven separately by `BlobDeleteDriver`).
   */
  suspend fun delete(id: BlobId)

  /** Observe all non-deleted blobs in the given scope path. */
  fun observeForScope(scopePath: String): Flow<List<BlobRef>>

  /** Reset `upload_attempts` to 0 so the uploader will retry on its next pass. */
  suspend fun resetUploadAttempts(id: BlobId)

  /** Delete all local blob files and rows for a given user uid. Called on wipe/sign-out. */
  suspend fun wipeForUser(uid: String)
}

/** Returned by [LocalBlobStore.installDownloaded] when the downloaded bytes' sha256 mismatches. */
class IntegrityError(
  val expected: String,
  val actual: String,
) : Exception("sha256 mismatch: expected=$expected, actual=$actual")
