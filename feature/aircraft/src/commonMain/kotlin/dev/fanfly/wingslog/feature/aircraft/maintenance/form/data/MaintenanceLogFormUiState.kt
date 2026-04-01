package dev.fanfly.wingslog.feature.aircraft.maintenance.form.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.MaintenanceLog
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
  val selectedComponentType: MaintenanceLog.ComponentType = MaintenanceLog.ComponentType.UNKNOWN,
  val selectedSubComponent: String? = null,
  val error: String? = null,
  /** All inspection cards for this aircraft — used by InspectionPickerSheet */
  val availableInspectionCards: List<InspectionCard> = emptyList(),
  val showInspectionPicker: Boolean = false,
  /** The maintenance date for this log entry (date only, no time). Null means "use current time". */
  val maintenanceDate: LocalDate? = null,
)
