package dev.fanfly.wingslog.feature.maintenance.update.logs.viewmodel

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.attachments.model.PendingAttachment
import dev.fanfly.wingslog.core.attachments.model.fileCount
import dev.fanfly.wingslog.core.attachments.model.visible
import dev.fanfly.wingslog.core.ui.common.UiText
import kotlinx.datetime.LocalDate

data class MaintenanceLogFormUiState(
  val isLoading: Boolean = false,
  val isSaving: Boolean = false,
  val workDescription: String = "",
  val selectedInspectionIds: List<String> = emptyList(),
  val engineTime: String = "",
  val airframeTime: String = "",
  val propTime: String = "",
  val aircraft: Aircraft? = null,
  val selectedComponentType: MaintenanceLog.ComponentType = MaintenanceLog.ComponentType.AIRFRAME,
  val selectedSubComponent: String? = null,
  val error: UiText? = null,
  /** All inspection cards for this aircraft — used by InspectionPickerSheet */
  val availableInspectionCards: List<InspectionCard> = emptyList(),
  val showInspectionPicker: Boolean = false,
  val selectedTechnician: Technician? = null,
  val availableTechnicians: List<Technician> = emptyList(),
  val showTechnicianPicker: Boolean = false,
  /** The maintenance date for this log entry (date only, no time). Null means "use current time". */
  val maintenanceDate: LocalDate? = null,
  /** Attachments pending in this editing session. Excludes PendingDelete items from the visible list. */
  val pendingAttachments: List<PendingAttachment> = emptyList(),
  val showAttachmentPicker: Boolean = false,
  /** Whether the current user is anonymous (attachments disabled for anonymous users). */
  val isAnonymous: Boolean = false,
  /** Snapshot of the form taken once after initial load — used to detect unsaved changes. */
  val initialSnapshot: FormSnapshot? = null,
) {
  val visibleAttachments: List<PendingAttachment> get() = pendingAttachments.visible()
  val fileAttachmentCount: Int get() = pendingAttachments.fileCount()
  val filesAtLimit: Boolean get() = fileAttachmentCount >= MAX_FILE_ATTACHMENTS

  fun currentSnapshot(): FormSnapshot = FormSnapshot(
    workDescription = workDescription,
    selectedInspectionIds = selectedInspectionIds,
    engineTime = engineTime,
    airframeTime = airframeTime,
    propTime = propTime,
    selectedComponentType = selectedComponentType,
    selectedSubComponent = selectedSubComponent,
    selectedTechnicianId = selectedTechnician?.id,
    maintenanceDate = maintenanceDate,
    visibleAttachments = pendingAttachments.visible(),
  )

  val hasChanges: Boolean
    get() = initialSnapshot != null && currentSnapshot() != initialSnapshot

  data class FormSnapshot(
    val workDescription: String,
    val selectedInspectionIds: List<String>,
    val engineTime: String,
    val airframeTime: String,
    val propTime: String,
    val selectedComponentType: MaintenanceLog.ComponentType,
    val selectedSubComponent: String?,
    val selectedTechnicianId: String?,
    val maintenanceDate: LocalDate?,
    val visibleAttachments: List<PendingAttachment>,
  )

  companion object {
    const val MAX_FILE_ATTACHMENTS = 3
    const val MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024 // 25 MB
  }
}
