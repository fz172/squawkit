package dev.fanfly.wingslog.feature.aircraft.dashboard.data

import dev.fanfly.wingslog.aircraft.Attachment
import dev.fanfly.wingslog.feature.inspection.model.InspectionCardWithStatus

sealed interface AircraftOverviewAction {
  data object BackClick : AircraftOverviewAction
  data class EditClick(val aircraftId: String) : AircraftOverviewAction
  data object DeleteConfirm : AircraftOverviewAction
  data class AddLogClick(val aircraftId: String) : AircraftOverviewAction
  data class EditLogClick(val aircraftId: String, val logId: String) : AircraftOverviewAction
  data class AddInspectionClick(val aircraftId: String) : AircraftOverviewAction
  data class InspectionCardClick(val card: InspectionCardWithStatus) : AircraftOverviewAction
  data object DismissInspectionDetail : AircraftOverviewAction
  data class EditInspectionClick(val aircraftId: String, val cardId: String) :
    AircraftOverviewAction

  data object CancelDeleteInspection : AircraftOverviewAction
  data object ConfirmDeleteInspection : AircraftOverviewAction
  data class AttachmentTap(val attachment: Attachment) : AircraftOverviewAction
}
