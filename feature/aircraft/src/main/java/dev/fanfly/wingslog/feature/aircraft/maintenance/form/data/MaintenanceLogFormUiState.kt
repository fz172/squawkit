package dev.fanfly.wingslog.feature.aircraft.maintenance.form.data

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog

data class MaintenanceLogFormUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val workDescription: String = "",
    val inspections: List<MaintenanceLog.InspectionType> = emptyList(),
    val tachTime: String = "",
    val airframeTime: String = "",
    val propTime: String = "",
    val aircraft: Aircraft? = null,
    val selectedComponentType: MaintenanceLog.ComponentType = MaintenanceLog.ComponentType.UNKNOWN,
    val selectedSubComponent: String? = null,
    val error: String? = null
)
