package dev.fanfly.wingslog.feature.tasks.model

import dev.fanfly.wingslog.aircraft.MaintenanceTask

data class MaintenanceTaskWithStatus(
  val card: MaintenanceTask,
  val dueStatus: DueMetadata,
)
