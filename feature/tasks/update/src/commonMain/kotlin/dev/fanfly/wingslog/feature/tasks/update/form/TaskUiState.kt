package dev.fanfly.wingslog.feature.tasks.update.form

import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.ManualMeterReading

sealed interface TaskUiState {
  data object Loading : TaskUiState
  data class Success(
    val thingId: String,
    val allInspections: List<MaintenanceTask> = emptyList(),
    val availableLogs: List<MaintenanceLog> = emptyList(),
    /** Readings set by hand on the dashboard; with the logs, what a meter rule is due against. */
    val manualReadings: List<ManualMeterReading> = emptyList(),
    val currentEngineHours: Float,
    /** The current reading of every meter that has one, by key — for the form's banner. */
    val currentReadings: Map<String, Float> = emptyMap(),
    val error: UiText? = null,
  ) : TaskUiState
}
