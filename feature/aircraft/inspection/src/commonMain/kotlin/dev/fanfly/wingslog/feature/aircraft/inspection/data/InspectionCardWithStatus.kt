package dev.fanfly.wingslog.feature.aircraft.inspection.data

import dev.fanfly.wingslog.aircraft.InspectionCard
import dev.fanfly.wingslog.aircraft.DueMetadata

data class InspectionCardWithStatus(
  val card: InspectionCard,
  val dueStatus: DueMetadata,
)
