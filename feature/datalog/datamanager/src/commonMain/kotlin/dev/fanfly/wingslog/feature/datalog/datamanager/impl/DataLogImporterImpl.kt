package dev.fanfly.wingslog.feature.datalog.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.datetime.toInstant
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.file.GzipCodec
import dev.fanfly.wingslog.core.file.sha256Hex
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.storage.EntityStore
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.core.storage.blob.BlobId
import dev.fanfly.wingslog.core.storage.blob.LocalBlobStore
import dev.fanfly.wingslog.core.storage.blob.UploadScheduler
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.datalog.DataLogEncoding
import dev.fanfly.wingslog.feature.attachment.datamanager.FileByteReader
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogImporter
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogParseException
import dev.fanfly.wingslog.feature.datalog.datamanager.DerivedFields
import dev.fanfly.wingslog.feature.datalog.datamanager.HeaderSniffer
import dev.fanfly.wingslog.feature.datalog.datamanager.ThingIdentifierLookup
import dev.fanfly.wingslog.feature.datalog.model.ImportFailure
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import dev.fanfly.wingslog.id.value
import dev.fanfly.wingslog.id.UserId
import dev.fanfly.wingslog.thing.Attachment
import dev.fanfly.wingslog.thing.AttachmentType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Reading → Sniffing → Parsing → Checking → Encoding → Storing → Done (design §6.1).
 *
 * Scope comes from the resolver, never from the signed-in uid: on a shared Thing the bytes and
 * the record belong in the host's tree. No quota check (PRD R14).
 */
class DataLogImporterImpl(
  private val fileByteReader: FileByteReader,
  private val sniffer: HeaderSniffer,
  private val scopeResolver: ThingScopeResolver,
  private val store: EntityStore<DataLog>,
  private val blobs: LocalBlobStore,
  private val scheduler: UploadScheduler?,
  private val identifiers: ThingIdentifierLookup,
  private val auth: AuthManager,
  private val clock: Clock = Clock.System,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DataLogImporter {

  override fun import(thingId: ThingId, picked: PickedFile, confirmDuplicate: Boolean): Flow<ImportProgress> =
    flow {
      emit(ImportProgress.Reading)
      val bytes = withContext(dispatcher) { fileByteReader.readBytes(picked.uri) }
      if (bytes == null || bytes.isEmpty()) {
        emit(ImportProgress.Failed(ImportFailure.UNREADABLE))
        return@flow
      }
      val parser = sniffer.sniff(bytes)
      if (parser == null) {
        emit(ImportProgress.Failed(ImportFailure.UNRECOGNIZED))
        return@flow
      }

      emit(ImportProgress.Parsing(0))
      val parsed = try {
        withContext(dispatcher) { parser.parse(bytes, picked.name) }
      } catch (e: DataLogParseException) {
        logger.w(e) { "Data log parse failed for ${picked.name}" }
        emit(ImportProgress.Failed(ImportFailure.PARSE_ERROR))
        return@flow
      }

      val rawSha256 = withContext(dispatcher) { sha256Hex(bytes) }
      val scope = scopeResolver.resolveNow(thingId.value)
      val existing = store.observeAll(scope).first().map { it.value }
      if (existing.any { it.raw_sha256 == rawSha256 }) {
        emit(ImportProgress.Failed(ImportFailure.DUPLICATE))
        return@flow
      }
      if (!confirmDuplicate) {
        val probable = existing.firstOrNull {
          it.source?.system_id == parsed.source.system_id && it.start?.toInstant() == parsed.start
        }
        val probableId = probable?.id
        if (probableId != null) {
          emit(ImportProgress.NeedsConfirmation(probableId))
          return@flow
        }
      }

      emit(ImportProgress.Storing)
      val gzip = GzipCodec.isAvailable()
      val stored = if (gzip) GzipCodec.compress(bytes) else bytes
      val contentType = if (gzip) "application/gzip" else "text/csv"
      // A fresh id, never the record's: a log payload can then never name a real data-log blob.
      val blobId = generateRandomId()
      val ref = blobs.put(BlobId(blobId), stored, contentType, scope)
      scheduler?.scheduleUpload(BlobId(blobId))

      val now = clock.now().toWireInstant()
      val end = DerivedFields.endPosition(parsed)
      val id = DataLogId(generateRandomId())
      val record = DataLog(
        id = id,
        format = parsed.format,
        parser_version = parsed.parserVersion,
        source = parsed.source,
        start = parsed.start.toWireInstant(),
        utc_offset_minutes = parsed.utcOffsetMinutes,
        duration_seconds = parsed.durationSeconds,
        sample_count = parsed.sampleCount,
        sample_rate_hz = parsed.sampleRateHz,
        series = parsed.series,
        raw_file = Attachment(
          id = blobId,
          name = picked.name,
          type = AttachmentType.ATTACHMENT_TYPE_FILE,
          storage_path = "${scope.toPath().trim('/')}/blobs/$blobId",
          mime_type = contentType,
          size_bytes = ref.sizeBytes,
          sha256 = ref.sha256,
          created_at = now,
        ),
        encoding = if (gzip) DataLogEncoding.DATA_LOG_ENCODING_GZIP else DataLogEncoding.DATA_LOG_ENCODING_NONE,
        raw_sha256 = rawSha256,
        raw_size_bytes = bytes.size.toLong(),
        file_name = picked.name,
        identity_mismatch = DerivedFields.identityMismatch(parsed.source.identity, identifiers.identifierOf(thingId)),
        airborne = DerivedFields.airborne(parsed),
        start_location_ident = DerivedFields.startLocationIdent(picked.name),
        end_latitude = end?.first ?: 0.0,
        end_longitude = end?.second ?: 0.0,
        created_at = now,
        created_by = auth.getCurrentUser()?.uid?.let { UserId(it) },
      )
      store.put(id.value, record, scope)
      emit(ImportProgress.Done(id))
    }

  private companion object {
    val logger = Logger.withTag("DataLogImporter")
  }
}
