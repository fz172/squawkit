package dev.fanfly.wingslog.feature.logs.update.logs.viewmodel

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.attachment.datamanager.AttachmentFormController
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.feature.attachment.model.fileCount
import dev.fanfly.wingslog.feature.attachment.model.visible
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
  val selectedComponentType: ComponentType = ComponentType.COMPONENT_AIRFRAME,
  val selectedSubComponent: String? = null,
  val error: UiText? = null,
  /** Open squawks for this aircraft — used by SquawkPickerSheet */
  val availableSquawks: List<Squawk> = emptyList(),
  val selectedSquawkIds: List<String> = emptyList(),
  val showSquawkPicker: Boolean = false,
  /** All inspection cards for this aircraft — used by TaskPickerSheet */
  val availableInspectionCards: List<MaintenanceTask> = emptyList(),
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
  val technicianEnabled: Boolean = true,
  /** Whether attachment uploads are enabled via Feature Lab. */
  val attachmentUploadEnabled: Boolean = true,
  /** Snapshot of the form taken once after initial load — used to detect unsaved changes. */
  val initialSnapshot: FormSnapshot? = null,
) {
  val visibleAttachments: List<PendingAttachment> get() = pendingAttachments.visible()
  val fileAttachmentCount: Int get() = pendingAttachments.fileCount()
  val filesAtLimit: Boolean
    get() = fileAttachmentCount >= AttachmentFormController.MAX_FILE_ATTACHMENTS

  fun currentSnapshot(): FormSnapshot = FormSnapshot(
    workDescription = workDescription,
    selectedSquawkIds = selectedSquawkIds,
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
    val selectedSquawkIds: List<String>,
    val selectedInspectionIds: List<String>,
    val engineTime: String,
    val airframeTime: String,
    val propTime: String,
    val selectedComponentType: ComponentType,
    val selectedSubComponent: String?,
    val selectedTechnicianId: String?,
    val maintenanceDate: LocalDate?,
    val visibleAttachments: List<PendingAttachment>,
  )
}
