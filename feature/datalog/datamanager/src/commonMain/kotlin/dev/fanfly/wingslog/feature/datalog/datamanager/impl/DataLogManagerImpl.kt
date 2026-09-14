package dev.fanfly.wingslog.feature.datalog.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.datetime.toInstant
import dev.fanfly.wingslog.core.file.GzipCodec
import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.core.storage.CollectionKind
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.EntityStoreFactory
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.storage.blob.BlobFilesystem
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.BlobRef
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.RemoteState
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.datalog.DataLogEncoding
import dev.fanfly.wingslog.feature.attachment.model.BlobSyncState
import dev.fanfly.wingslog.feature.attachment.model.DownloadState
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogCache
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogImporter
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogParser
import dev.fanfly.wingslog.feature.datalog.model.DataLogSeriesData
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class DataLogManagerImpl(
  private val scopeResolver: ThingScopeResolver,
  storeFactory: EntityStoreFactory,
  private val blobs: LocalBlobStore,
  private val filesystem: BlobFilesystem,
  private val scheduler: UploadScheduler?,
  private val importer: DataLogImporter,
  private val cache: DataLogCache,
  private val parsers: List<DataLogParser>,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DataLogManager {

  private val store: EntityStore<DataLog> =
    storeFactory.create(CollectionKind.DataLog)

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun observe(thingId: ThingId): Flow<List<DataLog>> =
    scopeResolver.resolve(thingId.value)
      .flatMapLatest { scope ->
        if (scope == null) {
          flowOf(emptyList())
        } else {
          store.observeAll(scope)
            .map { rows ->
              rows.map { it.value }
                .filter { it.id != null }
                .sortedByDescending { it.start?.toInstant() }
            }
            .catch { e ->
              logger.w(e) { "Error observing data logs" }
              emit(emptyList())
            }
        }
      }

  override fun observeOne(thingId: ThingId, id: DataLogId): Flow<DataLog?> =
    observe(thingId).map { logs -> logs.firstOrNull { it.id == id } }

  override fun import(
    thingId: ThingId,
    file: PickedFile,
    confirmDuplicate: Boolean
  ): Flow<ImportProgress> =
    importer.import(thingId, file, confirmDuplicate)

  override fun ensureLocal(
    thingId: ThingId,
    id: DataLogId
  ): Flow<DownloadState> = flow {
    val blobId = rawBlobId(thingId, id)
    if (blobId == null) {
      emit(DownloadState.Failed(NoSuchElementException("No data log $id")))
      return@flow
    }
    emitAll(
      blobs.observe(blobId)
        .distinctUntilChanged { a, b -> a?.remoteState == b?.remoteState }
        // Only once the REMOTE_ONLY row exists: scheduling against a missing row is a no-op.
        .onEach { ref ->
          if (ref?.remoteState == RemoteState.RemoteOnly) scheduler?.scheduleDownload(
            blobId
          )
        }
        .map { ref ->
          when (ref?.remoteState) {
            RemoteState.Synced, RemoteState.LocalOnly, RemoteState.Uploading -> DownloadState.Done
            RemoteState.RemoteOnly, null -> DownloadState.Downloading(0f)
            RemoteState.RemoteMissing ->
              DownloadState.Failed(Exception("This data log's file is no longer available."))
          }
        },
    )
  }

  override suspend fun load(
    thingId: ThingId,
    id: DataLogId
  ): Result<DataLogSeriesData> = runCatching {
    cache.get(id)
      ?.let { return@runCatching it }
    val record = observeOne(thingId, id).first()
      ?: throw NoSuchElementException("No data log $id")
    val rawFile = record.raw_file ?: error("Data log $id has no raw file")
    val ref = blobs.get(BlobId(rawFile.id))
      ?: error("Data log $id is not indexed locally")
    if (ref.remoteState == RemoteState.RemoteOnly || ref.remoteState == RemoteState.RemoteMissing) {
      error("Data log $id is not downloaded")
    }
    val stored = filesystem.read(ref.relativePath)
    val bytes = when (record.encoding) {
      DataLogEncoding.DATA_LOG_ENCODING_GZIP -> GzipCodec.decompress(stored)
      else -> stored
    }
    val parser = parsers.firstOrNull { it.format == record.format }
      ?: error("No parser for ${record.format}")
    val parsed =
      withContext(dispatcher) { parser.parse(bytes, record.file_name) }
    cache.put(id, parsed.data)
    parsed.data
  }.onFailure { logger.w(it) { "Error loading data log" } }

  override suspend fun delete(thingId: ThingId, id: DataLogId): Result<Unit> =
    runCatching {
      val scope = scopeResolver.resolveNow(thingId.value)
      store.delete(id.value, scope)
      cache.remove(id)
      Unit
    }.onFailure { logger.w(it) { "Error deleting data log" } }

  override fun observeBlobState(
    thingId: ThingId,
    id: DataLogId
  ): Flow<BlobSyncState?> = flow {
    val blobId = rawBlobId(thingId, id)
    if (blobId == null) {
      emit(null)
      return@flow
    }
    emitAll(
      blobs.observe(blobId)
        .map { it?.toBlobSyncState() })
  }

  private suspend fun rawBlobId(thingId: ThingId, id: DataLogId): BlobId? =
    observeOne(thingId, id).first()?.raw_file?.id?.takeIf { it.isNotBlank() }
      ?.let(::BlobId)

  private fun BlobRef.toBlobSyncState(): BlobSyncState = when (remoteState) {
    RemoteState.LocalOnly -> if (uploadAttempts > 0) BlobSyncState.UploadFailed else BlobSyncState.PendingUpload
    RemoteState.Uploading -> BlobSyncState.Uploading
    RemoteState.Synced -> BlobSyncState.Synced
    RemoteState.RemoteOnly, RemoteState.RemoteMissing -> BlobSyncState.RemoteOnly
  }

  private companion object {
    val logger = Logger.withTag("DataLogManager")
  }
}
