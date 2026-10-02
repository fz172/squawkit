package dev.fanfly.wingslog.feature.tasks.model

import dev.fanfly.wingslog.task.MaintenanceTask

data class MaintenanceTaskWithStatus(
  val card: MaintenanceTask,
  val dueStatus: DueMetadata,
)
