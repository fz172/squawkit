package dev.fanfly.wingslog.feature.export.datamanager.impl

import dev.fanfly.wingslog.aircraft.Aircraft
import dev.fanfly.wingslog.aircraft.MaintenanceLog
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.Technician
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata

/**
 * Aggregated records needed to write one aircraft folder in an export archive.
 */
data class AircraftBundle(
  val aircraft: Aircraft,
  val logs: List<MaintenanceLog>,
  val tasks: List<MaintenanceTask>,
  val dueByTaskId: Map<String, DueMetadata>,
  val lastCompliedByTaskId: Map<String, MaintenanceLog>,
  val squawks: List<Squawk>,
  val tasksById: Map<String, MaintenanceTask>,
  val squawksById: Map<String, Squawk>,
  val techniciansById: Map<String, Technician>,
)
