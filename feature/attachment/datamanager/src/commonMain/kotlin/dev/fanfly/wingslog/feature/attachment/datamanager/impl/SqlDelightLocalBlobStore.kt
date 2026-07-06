package dev.fanfly.wingslog.feature.attachment.datamanager.impl

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.storage.DatabaseWriteLock
import dev.fanfly.wingslog.core.storage.EntityScope
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.db.Blob_object
import dev.fanfly.wingslog.core.storage.db.WingsLogDatabase
import dev.fanfly.wingslog.feature.attachment.datamanager.BlobFilesystem
import dev.fanfly.wingslog.feature.attachment.datamanager.BlobRef
import dev.fanfly.wingslog.feature.attachment.datamanager.IntegrityError
import dev.fanfly.wingslog.feature.attachment.datamanager.LocalBlobStore
import dev.fanfly.wingslog.feature.attachment.datamanager.blobRelativePath
import dev.fanfly.wingslog.feature.attachment.datamanager.sha256Hex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * SQLDelight + filesystem-backed [LocalBlobStore]. The DB row is the source of truth for "what
 * state is this blob in?"; the filesystem holds the bytes.
 *
 * Ordering on writes: file first, then row. If the file write succeeds and the row write fails,
 * we leak a file — the orphan GC pass (design §8.3) cleans it up. If file write fails, the row
 * is never inserted, so no observable state changes.
 */
@OptIn(ExperimentalTime::class)
class SqlDelightLocalBlobStore(
  private val db: WingsLogDatabase,
  private val fs: BlobFilesystem,
  private val ioContext: CoroutineContext,
  private val clock: Clock = Clock.System,
  private val writeLock: DatabaseWriteLock = DatabaseWriteLock(),
) : LocalBlobStore {

  override suspend fun put(
    id: BlobId,
    bytes: ByteArray,
    contentType: String?,
    scope: EntityScope,
  ): BlobRef {
    val rel = blobRelativePath(id.value)
    fs.write(rel, bytes)
    val sha = sha256Hex(bytes)
    val now = clock.now()
      .toEpochMilliseconds()
    writeLock.withLock {
      db.schemaQueries.upsertBlob(
        id = id.value,
        scope_path = scope.toPath(),
        relative_path = rel,
        content_type = contentType,
        size_bytes = bytes.size.toLong(),
        sha256 = sha,
        remote_state = RemoteState.LocalOnly,
        remote_path = null,
        upload_attempts = 0L,
        last_attempt_at = null,
        updated_at = now,
        deleted = false,
      )
    }
    return loadRef(id)
      ?: error("upsertBlob did not produce a row for id=${id.value}")
  }

  override suspend fun upsertRemoteOnly(
    id: BlobId,
    sha256: String,
    sizeBytes: Long,
    contentType: String?,
    scope: EntityScope,
  ) {
    val existing = db.schemaQueries.selectBlobById(id.value)
      .awaitAsOneOrNull()
    if (existing != null) {
      // §6.1: idempotent — never overwrite a row that has progressed past REMOTE_ONLY.
      return
    }
    val now = clock.now()
      .toEpochMilliseconds()
    writeLock.withLock {
      db.schemaQueries.upsertBlob(
        id = id.value,
        scope_path = scope.toPath(),
        relative_path = blobRelativePath(id.value),
        content_type = contentType,
        size_bytes = sizeBytes,
        sha256 = sha256,
        remote_state = RemoteState.RemoteOnly,
        remote_path = "${
          scope.toPath()
            .trim('/')
        }/blobs/${id.value}",
        upload_attempts = 0L,
        last_attempt_at = null,
        updated_at = now,
        deleted = false,
      )
    }
  }

  override fun observe(id: BlobId): Flow<BlobRef?> =
    db.schemaQueries.selectBlobById(id.value)
      .asFlow()
      .mapToOneOrNull(ioContext)
      .map { it?.toRef() }

  override suspend fun get(id: BlobId): BlobRef? = loadRef(id)

  override suspend fun localUri(id: BlobId): String? {
    val row = db.schemaQueries.selectBlobById(id.value)
      .awaitAsOneOrNull() ?: return null
    return when (row.remote_state) {
      RemoteState.LocalOnly, RemoteState.Uploading, RemoteState.Synced ->
        if (fs.exists(row.relative_path)) fs.uriFor(row.relative_path) else null

      RemoteState.RemoteOnly -> null
    }
  }

  override suspend fun markUploading(id: BlobId) {
    val row = requireRow(id)
    require(row.remote_state == RemoteState.LocalOnly) {
      "markUploading: invalid transition from ${row.remote_state.wireName}"
    }
    writeLock.withLock {
      db.schemaQueries.setBlobRemoteState(
        remote_state = RemoteState.Uploading,
        remote_path = row.remote_path,
        last_attempt_at = clock.now()
          .toEpochMilliseconds(),
        upload_attempts = row.upload_attempts,
        id = id.value,
      )
    }
  }

  override suspend fun markUploaded(id: BlobId, remotePath: String) {
    val row = requireRow(id)
    require(row.remote_state == RemoteState.Uploading) {
      "markUploaded: invalid transition from ${row.remote_state.wireName}"
    }
    writeLock.withLock {
      db.schemaQueries.setBlobRemoteState(
        remote_state = RemoteState.Synced,
        remote_path = remotePath,
        last_attempt_at = clock.now()
          .toEpochMilliseconds(),
        upload_attempts = 0L,
        id = id.value,
      )
    }
  }

  override suspend fun markFailedTransient(id: BlobId) {
    val row = requireRow(id)
    require(row.remote_state == RemoteState.Uploading) {
      "markFailedTransient: invalid transition from ${row.remote_state.wireName}"
    }
    writeLock.withLock {
      db.schemaQueries.setBlobRemoteState(
        remote_state = RemoteState.LocalOnly,
        remote_path = row.remote_path,
        last_attempt_at = clock.now()
          .toEpochMilliseconds(),
        upload_attempts = row.upload_attempts + 1,
        id = id.value,
      )
    }
  }

  override suspend fun markFailedPermanent(id: BlobId, cause: Throwable) {
    val row = requireRow(id)
    require(row.remote_state == RemoteState.Uploading) {
      "markFailedPermanent: invalid transition from ${row.remote_state.wireName}"
    }
    Logger.w(throwable = cause) { "Permanent upload failure for blob ${id.value}" }
    writeLock.withLock {
      db.schemaQueries.setBlobRemoteState(
        remote_state = RemoteState.LocalOnly,
        remote_path = row.remote_path,
        last_attempt_at = clock.now()
          .toEpochMilliseconds(),
        upload_attempts = row.upload_attempts + 1,
        id = id.value,
      )
    }
  }

  override suspend fun installDownloaded(
    id: BlobId,
    bytes: ByteArray,
    expectedSha256: String,
  ): Result<Unit> {
    val row = requireRow(id)
    require(row.remote_state == RemoteState.RemoteOnly) {
      "installDownloaded: invalid transition from ${row.remote_state.wireName}"
    }
    val actual = sha256Hex(bytes)
    if (!actual.equals(expectedSha256, ignoreCase = true)) {
      return Result.failure(
        IntegrityError(
          expected = expectedSha256,
          actual = actual
        )
      )
    }
    fs.write(row.relative_path, bytes)
    writeLock.withLock {
      db.schemaQueries.setBlobRemoteState(
        remote_state = RemoteState.Synced,
        remote_path = row.remote_path,
        last_attempt_at = clock.now()
          .toEpochMilliseconds(),
        upload_attempts = 0L,
        id = id.value,
      )
    }
    return Result.success(Unit)
  }

  override suspend fun delete(id: BlobId) {
    val row = db.schemaQueries.selectBlobById(id.value)
      .awaitAsOneOrNull() ?: return
    fs.delete(row.relative_path)
    writeLock.withLock {
      db.schemaQueries.markBlobDeleted(
        updated_at = clock.now()
          .toEpochMilliseconds(),
        id = id.value,
      )
    }
  }

  private suspend fun requireRow(id: BlobId): Blob_object =
    db.schemaQueries.selectBlobById(id.value)
      .awaitAsOneOrNull()
      ?: error("blob_object row not found for id=${id.value}")

  private suspend fun loadRef(id: BlobId): BlobRef? =
    db.schemaQueries.selectBlobById(id.value)
      .awaitAsOneOrNull()
      ?.toRef()

  override fun observeForScope(scopePath: String): Flow<List<BlobRef>> =
    db.schemaQueries.selectBlobsForScope(scopePath)
      .asFlow()
      .mapToList(ioContext)
      .map { rows -> rows.map { it.toRef() } }

  override suspend fun resetUploadAttempts(id: BlobId) {
    writeLock.withLock { db.schemaQueries.resetUploadAttempts(id.value) }
  }

  override suspend fun wipeForUser(uid: String) {
    val prefix = "/users/$uid/%"
    val rows = db.schemaQueries.selectBlobsForScopePrefix(prefix)
      .awaitAsList()
    for (row in rows) {
      try {
        fs.delete(row.relative_path)
      } catch (_: Exception) {
      }
    }
    writeLock.withLock { db.schemaQueries.deleteBlobsForUser(prefix) }
  }

  private fun Blob_object.toRef(): BlobRef = BlobRef(
    id = BlobId(id),
    scope = EntityScope(
      scope_path.trim('/')
        .split('/')
    ),
    relativePath = relative_path,
    sizeBytes = size_bytes,
    sha256 = sha256,
    contentType = content_type,
    remoteState = remote_state,
    remotePath = remote_path,
    uploadAttempts = upload_attempts,
    deleted = deleted,
    updatedAt = Instant.fromEpochMilliseconds(updated_at),
  )
}
