package dev.fanfly.wingslog.core.ui.common.navigation

sealed class Screen(val route: String) {

  // Canonical navigation parameters
  companion object {
    const val AIRCRAFT_ID = "aircraftId"
    const val CARD_ID = "cardId"
    const val LOG_ID = "logId"
    const val TECHNICIAN_ID = "technicianId"
    const val SQUAWK_ID = "squawkId"

    const val CROSS_SCREEN_SUCCESS_MESSAGE = "success_message"
  }

  // Navigation route templates

  data object Login : Screen("login")
  data object NameEntry : Screen("name_entry")
  data object Welcome : Screen("welcome")
  data object Dashboard : Screen("main")
  data object Settings : Screen("settings")
  data object SyncSettings : Screen("sync_settings")
  data object ExportLogs : Screen("export_logs")
  data object AddAircraft : Screen("add_aircraft")

  data object ManageTechnicians : Screen("manage_technicians")

  data object FeatureLab : Screen("feature_lab")

  data object EditTechnician : Screen("edit_technician/{$TECHNICIAN_ID}") {
    fun createRoute(technicianId: String?) =
      "edit_technician/${technicianId ?: "new"}"
  }

  data object EditAircraft : Screen("edit_aircraft/{$AIRCRAFT_ID}") {
    fun createRoute(aircraftId: String) = "edit_aircraft/$aircraftId"
  }

  data object MaintenanceOverview :
    Screen("maintenance_overview/{$AIRCRAFT_ID}") {
    fun createRoute(aircraftId: String) = "maintenance_overview/$aircraftId"
  }

  data object AddMaintenanceTask :
    Screen("maintenance_task_create/{$AIRCRAFT_ID}") {
    fun createRoute(aircraftId: String) = "maintenance_task_create/$aircraftId"
  }

  data object EditMaintenanceTask :
    Screen("maintenance_task_edit/{$AIRCRAFT_ID}/{$CARD_ID}") {
    fun createRoute(
      aircraftId: String,
      cardId: String,
    ) =
      "maintenance_task_edit/$aircraftId/$cardId"
  }

  data object AddMaintenanceLog :
    Screen("maintenance_log_create/{$AIRCRAFT_ID}") {
    fun createRoute(aircraftId: String) = "maintenance_log_create/$aircraftId"
  }

  data object EditMaintenanceLog :
    Screen("maintenance_log_edit/{$AIRCRAFT_ID}/{$LOG_ID}") {
    fun createRoute(
      aircraftId: String,
      logId: String,
    ) = "maintenance_log_edit/$aircraftId/$logId"
  }

  data object AddSquawk : Screen("squawk_create/{$AIRCRAFT_ID}") {
    fun createRoute(aircraftId: String) = "squawk_create/$aircraftId"
  }

  data object EditSquawk : Screen("squawk_edit/{$AIRCRAFT_ID}/{$SQUAWK_ID}") {
    fun createRoute(aircraftId: String, squawkId: String) =
      "squawk_edit/$aircraftId/$squawkId"
  }
}
