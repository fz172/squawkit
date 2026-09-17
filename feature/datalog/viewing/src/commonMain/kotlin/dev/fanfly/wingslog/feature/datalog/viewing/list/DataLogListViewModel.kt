package dev.fanfly.wingslog.feature.datalog.viewing.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.DataLogImportSource
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.datetime.toInstant
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.ImportFailure
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import dev.fanfly.wingslog.feature.datalog.model.dataLogId
import dev.fanfly.wingslog.feature.datalog.viewing.analytics.DataLogImportTelemetry
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toLocalDateTime

/** PRD R40: upload needs a signed-in, non-anonymous account; viewing never does. */
enum class UploadGate { SignedIn, Guest }

/** One data log as the list renders it (PRD R34). */
data class DataLogRow(
  val id: DataLogId,
  /** The recorder's wall clock at the first row, in the recorder's own offset. */
  val startLocal: LocalDateTime,
  val airborne: Boolean,
  val startLocationIdent: String,
  val durationSeconds: Int,
  val product: String,
  val seriesCount: Int,
  val identityMismatch: Boolean,
  val fileName: String,
  val identity: String,
)

/** An upload in flight or one that ended in something the user must see (PRD R35). */
data class ImportRow(
  val key: Long,
  val file: PickedFile,
  val progress: ImportProgress,
)

sealed interface DataLogListEvent {
  data object DeleteFailed : DataLogListEvent
}

data class DataLogListUiState(
  val isLoading: Boolean = true,
  val rows: List<DataLogRow> = emptyList(),
  val uploadGate: UploadGate = UploadGate.Guest,
  val imports: List<ImportRow> = emptyList(),
  /** The row whose delete is awaiting confirmation. */
  val deleting: DataLogRow? = null,
)

class DataLogListViewModel(
  private val manager: DataLogManager,
  private val auth: AuthManager,
  analytics: AnalyticsManager,
  templates: CurrentThingTemplate,
  private val thingId: ThingId,
) : ViewModel() {

  private val telemetry =
    DataLogImportTelemetry(analytics, templates, DataLogImportSource.LIST)

  private val imports = MutableStateFlow<List<ImportRow>>(emptyList())
  private val loaded = MutableStateFlow(false)
  private val deleting = MutableStateFlow<DataLogRow?>(null)
  private var nextImportKey = 0L

  private val _events =
    MutableSharedFlow<DataLogListEvent>(extraBufferCapacity = 1)
  val events: SharedFlow<DataLogListEvent> = _events

  val uiState: StateFlow<DataLogListUiState> =
    combine(
      manager.observe(thingId),
      imports,
      loaded,
      deleting
    ) { logs, imports, loaded, deleting ->
      DataLogListUiState(
        isLoading = !loaded,
        rows = logs.map { it.toDataLogRow() },
        uploadGate = currentGate(),
        imports = imports,
        deleting = deleting,
      )
    }.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5_000),
      DataLogListUiState(uploadGate = currentGate())
    )

  init {
    viewModelScope.launch {
      manager.observe(thingId)
        .collect { loaded.value = true }
    }
  }

  /** Starts one import per picked file; each becomes an [ImportRow] until it finishes cleanly. */
  fun upload(files: List<PickedFile>) {
    if (currentGate() != UploadGate.SignedIn) return
    files.forEach { file ->
      start(
        ImportRow(
          nextImportKey++,
          file,
          ImportProgress.Reading
        ), confirmDuplicate = false
      )
    }
  }

  /** The user answered "Keep both" to a probable duplicate. */
  fun confirmImport(key: Long) {
    val row = imports.value.firstOrNull { it.key == key } ?: return
    start(row.copy(progress = ImportProgress.Reading), confirmDuplicate = true)
  }

  /** PRD R12: re-run the import against the Thing whose identifier the file carries. */
  fun fileUnderOtherThing(key: Long) {
    val row = imports.value.firstOrNull { it.key == key } ?: return
    val target =
      (row.progress as? ImportProgress.OtherThing)?.candidate ?: return
    start(
      row.copy(progress = ImportProgress.Reading),
      confirmDuplicate = true,
      thingId = target
    )
  }

  /** PRD R12: the identity is wrong but the Thing is right; keep it here, mismatch flag and all. */
  fun keepHere(key: Long) {
    val row = imports.value.firstOrNull { it.key == key } ?: return
    start(
      row.copy(progress = ImportProgress.Reading),
      confirmDuplicate = true,
      keepIdentity = true
    )
  }

  fun dismissImport(key: Long) {
    imports.update { rows -> rows.filterNot { it.key == key } }
  }

  /** Swipe reveals Delete; the dialog it opens is what commits (the same shape as the other lists). */
  fun onDeleteClick(row: DataLogRow) {
    deleting.value = row
  }

  fun cancelDelete() {
    deleting.value = null
  }

  fun confirmDelete() {
    val row = deleting.value ?: return
    deleting.value = null
    viewModelScope.launch {
      manager.delete(thingId, row.id)
        .onFailure { _events.tryEmit(DataLogListEvent.DeleteFailed) }
    }
  }

  private fun start(
    row: ImportRow,
    confirmDuplicate: Boolean,
    keepIdentity: Boolean = false,
    thingId: ThingId = this.thingId,
  ) {
    imports.update { rows -> rows.filterNot { it.key == row.key } + row }
    viewModelScope.launch {
      try {
        manager.import(thingId, row.file, confirmDuplicate, keepIdentity)
          .collect { progress ->
            if (progress is ImportProgress.Done) {
              dismissImport(row.key)
              // The parser's answer for duration and series, so it is read back from the store.
              manager.observeOne(thingId, progress.id)
                .first()
                ?.let { telemetry.imported(it, row.file) }
            } else {
              if (progress is ImportProgress.Failed) telemetry.failed(
                progress.reason,
                row.file
              )
              imports.update { rows ->
                rows.map {
                  if (it.key == row.key) it.copy(
                    progress = progress
                  ) else it
                }
              }
            }
          }
      } catch (e: Exception) {
        logger.w(e) { "Import failed" }
        telemetry.failed(ImportFailure.PARSE_ERROR, row.file)
        imports.update { rows ->
          rows.map {
            if (it.key == row.key) it.copy(
              progress = ImportProgress.Failed(
                ImportFailure.PARSE_ERROR
              )
            ) else it
          }
        }
      }
    }
  }

  private fun currentGate(): UploadGate {
    val user = auth.getCurrentUser() ?: return UploadGate.Guest
    return if (user.isAnonymous) UploadGate.Guest else UploadGate.SignedIn
  }

  private companion object {
    val logger = Logger.withTag("DataLogList")
  }
}

fun DataLog.toDataLogRow(): DataLogRow {
  val zone: TimeZone = UtcOffset(minutes = utc_offset_minutes).asTimeZone()
  val start = start?.toInstant()
    ?.toLocalDateTime(zone) ?: LocalDateTime(1970, 1, 1, 0, 0)
  return DataLogRow(
    id = dataLogId,
    startLocal = start,
    airborne = airborne,
    startLocationIdent = start_location_ident,
    durationSeconds = duration_seconds,
    product = source?.product.orEmpty(),
    seriesCount = series.size,
    identityMismatch = identity_mismatch,
    fileName = file_name,
    identity = source?.identity.orEmpty(),
  )
}
