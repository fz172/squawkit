package dev.fanfly.wingslog.feature.datalog.viewing.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.datetime.toInstant
import dev.fanfly.wingslog.datalog.DataLog
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.ImportFailure
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import dev.fanfly.wingslog.feature.datalog.model.dataLogId
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

data class DataLogListUiState(
  val isLoading: Boolean = true,
  val rows: List<DataLogRow> = emptyList(),
  val uploadGate: UploadGate = UploadGate.Guest,
  val query: String = "",
  val imports: List<ImportRow> = emptyList(),
) {
  /** Rows that match [query]; every row when the query is blank (PRD R2a). */
  val visibleRows: List<DataLogRow>
    get() {
      val q = query.trim()
      if (q.isEmpty()) return rows
      return rows.filter { it.matches(q) }
    }
}

class DataLogListViewModel(
  private val manager: DataLogManager,
  private val auth: AuthManager,
  private val thingId: ThingId,
) : ViewModel() {

  private val query = MutableStateFlow("")
  private val imports = MutableStateFlow<List<ImportRow>>(emptyList())
  private val loaded = MutableStateFlow(false)
  private var nextImportKey = 0L

  val uiState: StateFlow<DataLogListUiState> =
    combine(
      manager.observe(thingId),
      query,
      imports,
      loaded
    ) { logs, q, imports, loaded ->
      DataLogListUiState(
        isLoading = !loaded,
        rows = logs.map { it.toRow() },
        uploadGate = currentGate(),
        query = q,
        imports = imports,
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

  fun onQueryChange(value: String) {
    query.value = value
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

  fun dismissImport(key: Long) {
    imports.update { rows -> rows.filterNot { it.key == key } }
  }

  private fun start(row: ImportRow, confirmDuplicate: Boolean) {
    imports.update { rows -> rows.filterNot { it.key == row.key } + row }
    viewModelScope.launch {
      try {
        manager.import(thingId, row.file, confirmDuplicate)
          .collect { progress ->
            if (progress is ImportProgress.Done) {
              dismissImport(row.key)
            } else {
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

internal fun DataLog.toRow(): DataLogRow {
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

/** Date, identifier, tail number, product or file name (PRD R2a). Attached-record titles join in P5. */
internal fun DataLogRow.matches(query: String): Boolean {
  val q = query.lowercase()
  val date = startLocal.date.toString()
  return date.contains(q) ||
    startLocationIdent.lowercase()
      .contains(q) ||
    identity.lowercase()
      .contains(q) ||
    product.lowercase()
      .contains(q) ||
    fileName.lowercase()
      .contains(q)
}
