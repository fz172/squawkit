package dev.fanfly.wingslog.feature.maintenance.viewing.overview.data

sealed interface AircraftOverviewEvent {

  data object NavigateBack : AircraftOverviewEvent
  
  data class ShowError(val message: String?) : AircraftOverviewEvent
  data class NavigateToEditAircraft(val aircraftId: String) : AircraftOverviewEvent
  data class NavigateToLogDetails(val aircraftId: String) : AircraftOverviewEvent
  data class NavigateToAddLog(val aircraftId: String) : AircraftOverviewEvent
  data class NavigateToAddInspection(val aircraftId: String) : AircraftOverviewEvent
  data class NavigateToEditInspection(val aircraftId: String, val cardId: String) :
    AircraftOverviewEvent
}