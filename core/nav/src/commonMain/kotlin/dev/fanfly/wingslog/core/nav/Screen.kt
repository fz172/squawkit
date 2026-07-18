package dev.fanfly.wingslog.core.nav

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
  data object AdaptiveShell : Screen("app")
  data object SyncSettings : Screen("sync_settings")
  data object ExportLogs : Screen("export_logs")
  data object ExportHistory : Screen("export_history")
  data object AddAircraft : Screen("add_aircraft")
  data object EnterInviteCode : Screen("enter_invite_code")

  data object ManageTechnicians : Screen("manage_technicians")

  data object FeatureLab : Screen("feature_lab")

  data object EditTechnician : Screen("edit_technician/{$TECHNICIAN_ID}") {
    fun createRoute(technicianId: String?) =
      "edit_technician/${technicianId ?: "new"}"
  }

  data object EditAircraft : Screen("edit_aircraft/{$AIRCRAFT_ID}") {
    fun createRoute(aircraftId: String) = "edit_aircraft/$aircraftId"
  }

  data object ManageAccess : Screen("manage_access/{$AIRCRAFT_ID}") {
    fun createRoute(aircraftId: String) = "manage_access/$aircraftId"
  }

  data object InviteToAircraft : Screen("invite_to_aircraft/{$AIRCRAFT_ID}") {
    fun createRoute(aircraftId: String) = "invite_to_aircraft/$aircraftId"
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
    Screen("maintenance_log_create/{$AIRCRAFT_ID}?$SQUAWK_ID={$SQUAWK_ID}") {
    fun createRoute(aircraftId: String, squawkId: String? = null): String {
      val base = "maintenance_log_create/$aircraftId"
      return if (squawkId != null) "$base?$SQUAWK_ID=$squawkId" else base
    }
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