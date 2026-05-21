package dev.fanfly.wingslog.feature.export.datamanager.impl

import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.export.ExportRecord
import dev.fanfly.wingslog.export.ExportRecordAircraft
import dev.fanfly.wingslog.export.ExportRecordDateRange
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.storage.FirebaseStorage
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class ExportHistoryRemoteRepository(
  private val auth: FirebaseAuth,
  private val firestore: FirebaseFirestore,
  private val storage: FirebaseStorage,
  private val clock: Clock = Clock.System,
) {

  private val log = Logger.withTag(TAG)

  suspend fun uploadAndSync(record: ExportRecord, archiveBytes: ByteArray): ExportRecord {
    val user = auth.currentUser ?: return record
    if (user.isAnonymous) return record

    val remotePath = remoteArchivePath(user.uid, record.export_id, record.file_name)
    return runCatching {
      storage.reference(remotePath).putData(archiveBytes.toFirebaseData())
      val synced = record.copy(
        remote_archive_ref = remotePath,
        delivery_state = record.delivery_state.ifBlank { ExportDeliveryStates.NOT_REQUESTED },
        remote_expires_at_epoch_millis = (clock.now() + 60.days).toEpochMilliseconds(),
      )
      document(user.uid, record.export_id).set(synced.toWire(user.uid))
      synced
    }.getOrElse { error ->
      log.w(error) { "export remote sync failed for ${record.export_id}" }
      record
    }
  }

  suspend fun listRemoteRecords(): List<ExportRecord> {
    val user = auth.currentUser ?: return emptyList()
    if (user.isAnonymous) return emptyList()
    return runCatching {
      collection(user.uid).get().documents.mapNotNull { snapshot ->
        runCatching { snapshot.data<ExportRecordWire>().toExportRecord() }
          .onFailure { error -> log.w(error) { "skipping malformed export manifest ${snapshot.id}" } }
          .getOrNull()
      }.sortedByDescending { it.created_at_epoch_millis }
    }.getOrElse { error ->
      log.w(error) { "remote export history read failed for ${user.uid}" }
      emptyList()
    }
  }

  private fun collection(uid: String) =
    firestore.collection("users").document(uid).collection("export_history")

  private fun document(uid: String, exportId: String) = collection(uid).document(exportId)

  private fun remoteArchivePath(uid: String, exportId: String, fileName: String): String =
    "users/$uid/exports/$exportId/$fileName"

  companion object {
    private const val TAG = "ExportHistoryRemoteRepo"
  }
}

@Serializable
private data class ExportRecordWire(
  val exportId: String,
  val uid: String,
  val fileName: String,
  val sizeBytes: Long,
  val createdAtEpochMillis: Long,
  val displayLocation: String,
  val formats: List<String> = emptyList(),
  val dateRange: ExportRecordDateRangeWire? = null,
  val aircraft: List<ExportRecordAircraftWire> = emptyList(),
  val remoteArchiveRef: String? = null,
  val destinationEmail: String? = null,
  val destinationEmailSource: String? = null,
  val deliveryState: String = ExportDeliveryStates.NOT_REQUESTED,
  val deliverySentAtEpochMillis: Long? = null,
  val deliveryFailureCode: String? = null,
  val deliveryFailureMessage: String? = null,
  val remoteExpiresAtEpochMillis: Long? = null,
)

@Serializable
private data class ExportRecordDateRangeWire(
  val kind: String,
  val months: Int = 0,
  val customStart: String = "",
  val customEnd: String = "",
)

@Serializable
private data class ExportRecordAircraftWire(
  val tailNumber: String,
  val makeModel: String,
)

private fun ExportRecord.toWire(uid: String) = ExportRecordWire(
  exportId = export_id,
  uid = uid,
  fileName = file_name,
  sizeBytes = size_bytes,
  createdAtEpochMillis = created_at_epoch_millis,
  displayLocation = display_location,
  formats = formats,
  dateRange = date_range?.toWire(),
  aircraft = aircraft.map { it.toWire() },
  remoteArchiveRef = remote_archive_ref.nullIfBlank(),
  destinationEmail = destination_email.nullIfBlank(),
  destinationEmailSource = destination_email_source.nullIfBlank(),
  deliveryState = delivery_state.ifBlank { ExportDeliveryStates.NOT_REQUESTED },
  deliverySentAtEpochMillis = delivery_sent_at_epoch_millis.takeIf { it > 0L },
  deliveryFailureCode = delivery_failure_code.nullIfBlank(),
  deliveryFailureMessage = delivery_failure_message.nullIfBlank(),
  remoteExpiresAtEpochMillis = remote_expires_at_epoch_millis.takeIf { it > 0L },
)

private fun ExportRecordWire.toExportRecord() = ExportRecord(
  export_id = exportId,
  file_path = "",
  file_name = fileName,
  size_bytes = sizeBytes,
  created_at_epoch_millis = createdAtEpochMillis,
  display_location = displayLocation,
  formats = formats,
  date_range = dateRange?.toProto(),
  aircraft = aircraft.map { it.toProto() },
  remote_archive_ref = remoteArchiveRef.orEmpty(),
  destination_email = destinationEmail.orEmpty(),
  destination_email_source = destinationEmailSource.orEmpty(),
  delivery_state = deliveryState,
  delivery_sent_at_epoch_millis = deliverySentAtEpochMillis ?: 0L,
  delivery_failure_code = deliveryFailureCode.orEmpty(),
  delivery_failure_message = deliveryFailureMessage.orEmpty(),
  remote_expires_at_epoch_millis = remoteExpiresAtEpochMillis ?: 0L,
)

private fun ExportRecordDateRange.toWire() = ExportRecordDateRangeWire(
  kind = kind,
  months = months,
  customStart = custom_start,
  customEnd = custom_end,
)

private fun ExportRecordDateRangeWire.toProto() = ExportRecordDateRange(
  kind = kind,
  months = months,
  custom_start = customStart,
  custom_end = customEnd,
)

private fun ExportRecordAircraft.toWire() = ExportRecordAircraftWire(
  tailNumber = tail_number,
  makeModel = make_model,
)

private fun ExportRecordAircraftWire.toProto() = ExportRecordAircraft(
  tail_number = tailNumber,
  make_model = makeModel,
)

private fun String.nullIfBlank(): String? = ifBlank { null }
