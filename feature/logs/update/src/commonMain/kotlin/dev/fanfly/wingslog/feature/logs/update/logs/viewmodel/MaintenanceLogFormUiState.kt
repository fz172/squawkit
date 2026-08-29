package dev.fanfly.wingslog.feature.logs.update.logs.viewmodel

import dev.fanfly.wingslog.aircraft.ComponentType
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.core.ui.common.UiText
import dev.fanfly.wingslog.feature.attachment.datamanager.QuotaChecker
import dev.fanfly.wingslog.feature.attachment.model.PendingAttachment
import dev.fanfly.wingslog.feature.attachment.model.fileCount
import dev.fanfly.wingslog.feature.attachment.model.visible
import dev.fanfly.wingslog.thing.Thing
import kotlinx.datetime.LocalDate

data class MaintenanceLogFormUiState(
  val isLoading: Boolean = false,
  val isSaving: Boolean = false,
  val workDescription: String = "",
  val selectedInspectionIds: List<String> = emptyList(),
  val engineTime: String = "",
  val airframeTime: String = "",
  val propTime: String = "",
  val thing: Thing? = null,
  val selectedComponentType: ComponentType = ComponentType.COMPONENT_AIRFRAME,
  val selectedSubComponent: String? = null,
  val error: UiText? = null,
  /** Open squawks for this thing, plus any already addressed by this log — used by SquawkPickerSheet and to resolve titles for selected squawk chips */
  val availableSquawks: List<Squawk> = emptyList(),
  val selectedSquawkIds: List<String> = emptyList(),
  val showSquawkPicker: Boolean = false,
  /** All inspection cards for this thing — used by TaskPickerSheet */
  val availableInspectionCards: List<MaintenanceTask> = emptyList(),
  val showInspectionPicker: Boolean = false,
  val selectedTechnician: Technician? = null,
  val availableTechnicians: List<Technician> = emptyList(),
  /** Members of this thing's share who published a mirror — selectable, badged "Linked" (§7.3). */
  val linkedTechnicians: List<Technician> = emptyList(),
  /** The caller's own technician record, listed first in the picker. */
  val selfTechnicianId: String? = null,
  val showTechnicianPicker: Boolean = false,
  /** The maintenance date for this log entry (date only, no time). Null means "use current time". */
  val maintenanceDate: LocalDate? = null,
  /** Attachments pending in this editing session. Excludes PendingDelete items from the visible list. */
  val pendingAttachments: List<PendingAttachment> = emptyList(),
  val showAttachmentPicker: Boolean = false,
  /**
   * Why the last pick dropped one or more files (over the cap, already attached, too large).
   * Kept apart from [error]: that one renders as the work-description field's supporting text on
   * the Work tab, where an attachment message would be both invisible and misleading.
   */
  val attachmentError: UiText? = null,
  /** Whether the current user is anonymous (attachments disabled for anonymous users). */
  val isAnonymous: Boolean = false,
  /** Whether file/photo attachment uploads are enabled via Developer Options; links are always on. */
  val attachmentUploadEnabled: Boolean = false,
  /** Snapshot of the form taken once after initial load — used to detect unsaved changes. */
  val initialSnapshot: FormSnapshot? = null,
  /**
   * Title of a squawk this log was opened to resolve (via the squawk edit screen's "Fixed"
   * option), pending resolution into a localized [workDescription] prefill by the screen. Null
   * once consumed (or if this log wasn't opened via that flow).
   */
  val pendingResolveSquawkTitle: String? = null,
  /**
   * Title of a task this log was opened to resolve (via the task edit screen's "Create Work
   * Log" option), pending resolution into a localized [workDescription] prefill by the screen.
   * Null once consumed (or if this log wasn't opened via that flow).
   */
  val pendingResolveTaskTitle: String? = null,
) {
  val visibleAttachments: List<PendingAttachment> get() = pendingAttachments.visible()
  val fileAttachmentCount: Int get() = pendingAttachments.fileCount()
  val filesAtLimit: Boolean
    get() = fileAttachmentCount >= QuotaChecker.MAX_FILE_ATTACHMENTS

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
