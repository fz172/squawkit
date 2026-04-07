package dev.fanfly.wingslog.core.ui.common.navigation

sealed class Screen(val route: String) {
  data object Login : Screen("login")
  data object Dashboard : Screen("main")
  data object Settings : Screen("settings")
  data object EditProfile : Screen("edit_profile")
  data object AddAircraft : Screen("add_aircraft")

  data object EditAircraft : Screen("edit_aircraft/{aircraft_id}") {
    fun createRoute(aircraftId: String) = "edit_aircraft/$aircraftId"
  }

  data object MaintenanceOverview : Screen("maintenance_overview/{aircraftId}") {
    fun createRoute(aircraftId: String) = "maintenance_overview/$aircraftId"
  }

  data object AddInspection : Screen("maintenance_inspection_create/{aircraftId}") {
    fun createRoute(aircraftId: String) = "maintenance_inspection_create/$aircraftId"
  }

  data object EditInspection : Screen("maintenance_inspection_edit/{aircraftId}/{cardId}") {
    fun createRoute(aircraftId: String, cardId: String) = "maintenance_inspection_edit/$aircraftId/$cardId"
  }

  data object MaintenanceLogs : Screen("maintenance_logs/{aircraftId}") {
    fun createRoute(aircraftId: String) = "maintenance_logs/$aircraftId"
  }

  data object AddMaintenanceLog : Screen("maintenance_log_create/{aircraftId}") {
    fun createRoute(aircraftId: String) = "maintenance_log_create/$aircraftId"
  }

  data object EditMaintenanceLog : Screen("maintenance_log_edit/{aircraftId}/{logId}") {
    fun createRoute(aircraftId: String, logId: String) = "maintenance_log_edit/$aircraftId/$logId"
  }
}
