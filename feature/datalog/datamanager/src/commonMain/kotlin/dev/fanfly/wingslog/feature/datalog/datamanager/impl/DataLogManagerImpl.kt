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
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.feature.datalog.datamanager.DerivedFields
import dev.fanfly.wingslog.feature.datalog.datamanager.ThingIdentifierLookup
import dev.fanfly.wingslog.feature.datalog.model.ParsedDataLog
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
  private val identifiers: ThingIdentifierLookup,
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
    confirmDuplicate: Boolean,
    keepIdentity: Boolean,
  ): Flow<ImportProgress> =
    importer.import(thingId, file, confirmDuplicate, keepIdentity)

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
    val parser = parsers.firstOrNull { record.format in it.formats }
      ?: error("No parser for ${record.format}")
    // Reading the file and inflating it are part of the work, not preliminaries to it. A 46 MB
    // SkyView download is about 6 MB on disk, and doing either on the caller's dispatcher froze the
    // viewer for seconds before it could draw so much as a spinner — the parse was already off the
    // main thread and was never the whole cost.
    //
    // Only this record's session is materialised. A SkyView download holds every power-on since the
    // last one, and building all of them to draw one is the difference between a second and a
    // minute on a phone.
    val parsed = withContext(dispatcher) {
      val stored = filesystem.read(ref.relativePath)
      val bytes = when (record.encoding) {
        DataLogEncoding.DATA_LOG_ENCODING_GZIP -> GzipCodec.decompress(stored)
        else -> stored
      }
      parser.parse(bytes, record.file_name, session = record.session_index)
    }.firstOrNull()
      ?: error("Data log $id has no session ${record.session_index}")
    refreshStoredRecord(thingId, record, parsed)
    cache.put(id, parsed.data)
    parsed.data
  }.onFailure { logger.w(it) { "Error loading data log" } }

  /**
   * Brings a stored record back in line with what this build would import today, on open.
   *
   * Two things go stale, for two different reasons.
   *
   * **The catalogue** — every series' name, unit, range and canonical id — is frozen into the record
   * at import, while the values are re-parsed on every open. So a parser fix reaches the charts
   * immediately and never reaches the sidebar, and a log imported before the fix shows a range that
   * disagrees with the line drawn beside it. `parser_version` is what tells those apart.
   *
   * **The identity mismatch** is a comparison against the Thing, so it goes stale when the Thing
   * changes rather than when the parser does — renaming a tail number never used to clear the flag
   * it invalidated. It is recomputed every time and written only when the answer moved.
   *
   * The blob, hashes and filename are nobody's to rewrite here.
   *
   * A failure is logged and swallowed: the caller asked for the data, which it already has.
   */
  private suspend fun refreshStoredRecord(
    thingId: ThingId,
    record: DataLog,
    parsed: ParsedDataLog,
  ) {
    runCatching {
      val staleCatalogue = record.parser_version != parsed.parserVersion
      val mismatch = DerivedFields.identityMismatch(
        parsed.source.identity,
        identifiers.identifierOf(thingId),
      )
      if (!staleCatalogue && mismatch == record.identity_mismatch) return
      val id = record.id ?: return
      val end = DerivedFields.endPosition(parsed)
      val scope = scopeResolver.resolveNow(thingId.value)
      val refreshed = if (staleCatalogue) {
        record.copy(
          parser_version = parsed.parserVersion,
          source = parsed.source,
          start = parsed.start.toWireInstant(),
          start_approximate = parsed.startApproximate,
          utc_offset_minutes = parsed.utcOffsetMinutes,
          duration_seconds = parsed.durationSeconds,
          sample_count = parsed.sampleCount,
          sample_rate_hz = parsed.sampleRateHz,
          series = parsed.series,
          airborne = DerivedFields.airborne(parsed),
          end_latitude = end?.first ?: 0.0,
          end_longitude = end?.second ?: 0.0,
        )
      } else {
        record
      }
      store.put(id.value, refreshed.copy(identity_mismatch = mismatch), scope)
    }.onFailure { logger.w(it) { "Could not refresh a stored data log" } }
  }

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
