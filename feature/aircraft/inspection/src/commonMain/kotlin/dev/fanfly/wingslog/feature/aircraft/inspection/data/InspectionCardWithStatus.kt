package dev.fanfly.wingslog.feature.aircraft.inspection.data

import dev.fanfly.wingslog.aircraft.InspectionCard

data class InspectionCardWithStatus(
  val card: InspectionCard,
  val dueStatus: DueMetadata,
)
