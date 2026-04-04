package dev.fanfly.wingslog.feature.inspection.model

import dev.fanfly.wingslog.aircraft.InspectionCard

data class InspectionCardWithStatus(
  val card: InspectionCard,
  val dueStatus: DueMetadata,
)
