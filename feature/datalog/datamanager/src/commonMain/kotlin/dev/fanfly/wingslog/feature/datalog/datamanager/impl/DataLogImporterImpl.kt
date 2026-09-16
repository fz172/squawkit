package dev.fanfly.wingslog.feature.datalog.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.datetime.toInstant
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.file.GzipCodec
import dev.fanfly.wingslog.core.file.sha256Hex
import dev.fanfly.wingslog.core.model.id.generateRandomId
import dev.fanfly.wingslog.core.model.id.value
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
import dev.fanfly.wingslog.feature.datalog.datamanager.OtherThingLookup
import dev.fanfly.wingslog.feature.datalog.datamanager.ThingIdentifierLookup
import dev.fanfly.wingslog.feature.datalog.model.ImportFailure
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
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
  private val otherThings: OtherThingLookup = OtherThingLookup { _, _ -> null },
  private val clock: Clock = Clock.System,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DataLogImporter {

  override fun import(
    thingId: ThingId,
    picked: PickedFile,
    confirmDuplicate: Boolean,
    keepIdentity: Boolean,
  ): Flow<ImportProgress> =
    flow {
      emit(ImportProgress.Reading)
      val bytes =
        withContext(dispatcher) { fileByteReader.readBytes(picked.uri) }
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
      val sessions = try {
        withContext(dispatcher) { parser.parse(bytes, picked.name) }
      } catch (e: DataLogParseException) {
        logger.w(e) { "Data log parse failed for ${picked.name}" }
        emit(ImportProgress.Failed(ImportFailure.PARSE_ERROR))
        return@flow
      }
      if (sessions.isEmpty()) {
        emit(ImportProgress.Failed(ImportFailure.PARSE_ERROR))
        return@flow
      }
      // Everything before the write is about the file, so it is asked once for the whole file and
      // not once per session: the same bytes, the same recorder, the same aeroplane.
      val parsed = sessions.first()

      val rawSha256 = withContext(dispatcher) { sha256Hex(bytes) }
      val scope = scopeResolver.resolveNow(thingId.value)
      val existing = store.observeAll(scope)
        .first()
        .map { it.value }
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

      val ownIdentifier = identifiers.identifierOf(thingId)
      val identityMismatch =
        DerivedFields.identityMismatch(parsed.source.identity, ownIdentifier)
      if (identityMismatch && !keepIdentity) {
        val other =
          otherThings.thingWithIdentifier(parsed.source.identity, thingId)
        if (other != null) {
          emit(ImportProgress.OtherThing(other.id, other.name))
          return@flow
        }
      }

      emit(ImportProgress.Storing)
      val gzip = GzipCodec.isAvailable()
      // Compressing the picked file is the same size of job as parsing it, and belongs in the same
      // place: off the caller's dispatcher.
      val stored =
        withContext(dispatcher) { if (gzip) GzipCodec.compress(bytes) else bytes }
      val contentType = if (gzip) "application/gzip" else "text/csv"
      // A fresh id, never the record's: a log payload can then never name a real data-log blob.
      val blobId = generateRandomId()
      val ref = blobs.put(BlobId(blobId), stored, contentType, scope)
      scheduler?.scheduleUpload(BlobId(blobId))

      val now = clock.now()
        .toWireInstant()
      val rawFile = Attachment(
        id = blobId,
        name = picked.name,
        type = AttachmentType.ATTACHMENT_TYPE_FILE,
        storage_path = "${
          scope.toPath()
            .trim('/')
        }/blobs/$blobId",
        mime_type = contentType,
        size_bytes = ref.sizeBytes,
        sha256 = ref.sha256,
        created_at = now,
      )
      val createdBy = auth.getCurrentUser()?.uid?.let { UserId(it) }
      // One record per session, all naming the one blob. A SkyView download holds every power-on
      // since the last one, and a month-wide time axis with a fortnight of empty space in the
      // middle is not a chart anyone can read.
      val ids = sessions.mapIndexed { index, session ->
        val end = DerivedFields.endPosition(session)
        val id = DataLogId(generateRandomId())
        store.put(
          id.value,
          DataLog(
            id = id,
            format = session.format,
            parser_version = session.parserVersion,
            source = session.source,
            start = session.start.toWireInstant(),
            start_approximate = session.startApproximate,
            session_index = index,
            utc_offset_minutes = session.utcOffsetMinutes,
            duration_seconds = session.durationSeconds,
            sample_count = session.sampleCount,
            sample_rate_hz = session.sampleRateHz,
            series = session.series,
            raw_file = rawFile,
            encoding = if (gzip) DataLogEncoding.DATA_LOG_ENCODING_GZIP else DataLogEncoding.DATA_LOG_ENCODING_NONE,
            raw_sha256 = rawSha256,
            raw_size_bytes = bytes.size.toLong(),
            file_name = picked.name,
            identity_mismatch = identityMismatch,
            airborne = DerivedFields.airborne(session),
            start_location_ident = DerivedFields.startLocationIdent(picked.name),
            end_latitude = end?.first ?: 0.0,
            end_longitude = end?.second ?: 0.0,
            created_at = now,
            created_by = createdBy,
          ),
          scope,
        )
        id
      }
      emit(ImportProgress.Done(ids.first(), sessionCount = ids.size))
    }

  private companion object {
    val logger = Logger.withTag("DataLogImporter")
  }
}
