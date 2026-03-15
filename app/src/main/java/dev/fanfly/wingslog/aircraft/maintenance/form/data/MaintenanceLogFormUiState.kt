package dev.fanfly.wingslog.aircraft.maintenance.form.data

import dev.fanfly.wingslog.aircraft.MaintenanceLog

data class MaintenanceLogFormUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val workDescription: String = "",
    val inspectionStatus: String = "",
    val tachTime: String = "",
    val componentType: MaintenanceLog.ComponentType = MaintenanceLog.ComponentType.UNKNOWN,
    val componentSerial: String = "",
    val error: String? = null
)
