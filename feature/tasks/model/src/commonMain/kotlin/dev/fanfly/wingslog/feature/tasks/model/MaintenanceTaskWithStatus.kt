package dev.fanfly.wingslog.feature.tasks.model

import dev.fanfly.wingslog.thing.MaintenanceTask

data class MaintenanceTaskWithStatus(
  val card: MaintenanceTask,
  val dueStatus: DueMetadata,
)
