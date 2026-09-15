package dev.fanfly.wingslog.feature.datalog.viewing.attach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.analytics.DataLogImportSource
import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.feature.attachment.model.PickedFile
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.model.ImportFailure
import dev.fanfly.wingslog.feature.datalog.model.ImportProgress
import dev.fanfly.wingslog.feature.datalog.viewing.analytics.DataLogImportTelemetry
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogRow
import dev.fanfly.wingslog.feature.datalog.viewing.list.ImportRow
import dev.fanfly.wingslog.feature.datalog.viewing.list.toDataLogRow
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataLogPickerUiState(
  val loaded: Boolean = false,
  val rows: List<DataLogRow> = emptyList(),
  val selected: DataLogId? = null,
  /** The one upload the picker runs at a time; null when idle. */
  val import: ImportRow? = null,
  /** PRD R40: upload needs a signed-in, non-anonymous account. */
  val canUpload: Boolean = false,
)

/** The attachment picker's data log list (design §9.2): a radio selection plus one inline upload. */
class DataLogAttachmentPickerViewModel(
  private val manager: DataLogManager,
  private val auth: AuthManager,
  analytics: AnalyticsManager,
  templates: CurrentThingTemplate,
  private val thingId: ThingId,
) : ViewModel() {

  private val telemetry =
    DataLogImportTelemetry(analytics, templates, DataLogImportSource.ATTACHMENT)

  private val selected = MutableStateFlow<DataLogId?>(null)
  private val import = MutableStateFlow<ImportRow?>(null)

  val uiState: StateFlow<DataLogPickerUiState> =
    combine(manager.observe(thingId), selected, import) { logs, selected, import ->
      DataLogPickerUiState(
        loaded = true,
        rows = logs.map { it.toDataLogRow() },
        selected = selected,
        import = import,
        canUpload = canUpload(),
      )
    }.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5_000),
      DataLogPickerUiState(canUpload = canUpload())
    )

  fun select(id: DataLogId) {
    selected.value = id
  }

  /** Imports the first picked file; a finished import selects the new record. */
  fun upload(files: List<PickedFile>) {
    val file = files.firstOrNull() ?: return
    if (!canUpload()) return
    start(ImportRow(0L, file, ImportProgress.Reading), confirmDuplicate = false)
  }

  /** The user answered "Keep both" to a probable duplicate. */
  fun confirmImport() {
    val row = import.value ?: return
    start(row.copy(progress = ImportProgress.Reading), confirmDuplicate = true)
  }

  fun dismissImport() {
    import.value = null
  }

  private fun start(row: ImportRow, confirmDuplicate: Boolean) {
    import.value = row
    viewModelScope.launch {
      try {
        // keepIdentity: the user picked this record's Thing, so R12's offer would be noise here.
        manager.import(thingId, row.file, confirmDuplicate, keepIdentity = true).collect { progress ->
          if (progress is ImportProgress.Done) {
            selected.value = progress.id
            import.value = null
            manager.observeOne(thingId, progress.id).first()
              ?.let { telemetry.imported(it, row.file) }
          } else {
            if (progress is ImportProgress.Failed) telemetry.failed(progress.reason, row.file)
            import.update { it?.copy(progress = progress) }
          }
        }
      } catch (e: Exception) {
        logger.w(e) { "Import failed" }
        telemetry.failed(ImportFailure.PARSE_ERROR, row.file)
        import.update { it?.copy(progress = ImportProgress.Failed(ImportFailure.PARSE_ERROR)) }
      }
    }
  }

  private fun canUpload(): Boolean {
    val user = auth.getCurrentUser() ?: return false
    return !user.isAnonymous
  }

  private companion object {
    val logger = Logger.withTag("DataLogPicker")
  }
}
