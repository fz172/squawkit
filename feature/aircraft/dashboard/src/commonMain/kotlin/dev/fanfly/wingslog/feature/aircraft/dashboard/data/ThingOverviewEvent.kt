package dev.fanfly.wingslog.feature.aircraft.dashboard.data

sealed interface AircraftOverviewEvent {

  data object NavigateBack : AircraftOverviewEvent

  data class ShowError(val message: String?) : AircraftOverviewEvent
  data class NavigateToEditAircraft(val thingId: String) :
    AircraftOverviewEvent

  data class NavigateToManageAccess(val thingId: String) :
    AircraftOverviewEvent

  data class NavigateToAddLog(val thingId: String) : AircraftOverviewEvent
  data class NavigateToEditLog(val thingId: String, val logId: String) :
    AircraftOverviewEvent

  data class NavigateToAddTask(val thingId: String) : AircraftOverviewEvent
  data class NavigateToEditTask(val thingId: String, val cardId: String) :
    AircraftOverviewEvent

  data class NavigateToAddSquawk(val thingId: String) : AircraftOverviewEvent
  data class NavigateToEditSquawk(
    val thingId: String,
    val squawkId: String
  ) : AircraftOverviewEvent
}
