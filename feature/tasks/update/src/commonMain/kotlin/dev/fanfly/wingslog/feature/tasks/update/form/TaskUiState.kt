package dev.fanfly.wingslog.feature.tasks.update.form

import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.thing.MaintenanceLog
import dev.fanfly.wingslog.thing.MaintenanceTask

sealed interface TaskUiState {
  data object Loading : TaskUiState
  data class Success(
    val thingId: String,
    val allInspections: List<MaintenanceTask> = emptyList(),
    val availableLogs: List<MaintenanceLog> = emptyList(),
    val currentEngineHours: Float,
    /** The latest reading of every meter the overview knows, by key — for the form's banner. */
    val currentReadings: Map<String, Float> = emptyMap(),
    val error: UiText? = null,
  ) : TaskUiState
}
