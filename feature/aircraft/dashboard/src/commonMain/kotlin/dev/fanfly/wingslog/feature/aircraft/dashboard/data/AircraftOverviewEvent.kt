package dev.fanfly.wingslog.feature.aircraft.dashboard.data

sealed interface AircraftOverviewEvent {

  data object NavigateBack : AircraftOverviewEvent

  data class ShowError(val message: String?) : AircraftOverviewEvent
  data class NavigateToEditAircraft(val aircraftId: String) :
    AircraftOverviewEvent

  data class NavigateToManageAccess(val aircraftId: String) :
    AircraftOverviewEvent

  data class NavigateToAddLog(val aircraftId: String) : AircraftOverviewEvent
  data class NavigateToEditLog(val aircraftId: String, val logId: String) :
    AircraftOverviewEvent

  data class NavigateToAddTask(val aircraftId: String) : AircraftOverviewEvent
  data class NavigateToEditTask(val aircraftId: String, val cardId: String) :
    AircraftOverviewEvent

  data class NavigateToAddSquawk(val aircraftId: String) : AircraftOverviewEvent
  data class NavigateToEditSquawk(
    val aircraftId: String,
    val squawkId: String
  ) : AircraftOverviewEvent
}
