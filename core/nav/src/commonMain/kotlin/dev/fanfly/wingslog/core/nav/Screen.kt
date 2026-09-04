package dev.fanfly.wingslog.core.nav

import dev.fanfly.wingslog.core.nav.Screen.Companion.TEMPLATE_ID


sealed class Screen(val route: String) {

  // Canonical navigation parameters
  companion object {
    const val THING_ID = "thingId"
    const val CARD_ID = "cardId"
    const val LOG_ID = "logId"
    const val TECHNICIAN_ID = "technicianId"
    const val SQUAWK_ID = "squawkId"
    const val TEMPLATE_ID = "templateId"

    const val CROSS_SCREEN_SUCCESS_MESSAGE = "success_message"
    /** A Thing the shell should switch to once a dialog closes — set by the create form. */
    const val CROSS_SCREEN_SELECT_THING_ID = "select_thing_id"
  }

  // Navigation route templates

  data object Login : Screen("login")
  data object AdaptiveShell : Screen("app")
  data object SyncSettings : Screen("sync_settings")
  data object Notifications : Screen("notifications")
  data object ExportLogs : Screen("export_logs")
  data object ExportHistory : Screen("export_history")

  /**
   * The create form. [TEMPLATE_ID] is optional so the empty state can still open the form directly;
   * absent, the form falls back the way it always did (#738).
   */
  data object AddThing : Screen("add_thing?$TEMPLATE_ID={$TEMPLATE_ID}") {
    fun createRoute(templateId: String? = null) =
      if (templateId.isNullOrEmpty()) "add_thing" else "add_thing?$TEMPLATE_ID=$templateId"
  }

  /**
   * The template's recommended schedule, offered once the Thing exists (PRD §4.9). Reached from
   * the create form's hand-off and from an empty Tasks tab; both read the pack off the Thing's own
   * DNA, so the id is all the route carries.
   */
  data object StarterPack : Screen("starter_pack/{$THING_ID}") {
    fun createRoute(thingId: String) = "starter_pack/$thingId"
  }

  data object EnterInviteCode : Screen("enter_invite_code")

  data object ManageTechnicians : Screen("manage_technicians")

  data object DeveloperOptions : Screen("developer_options")
  data object Subscription : Screen("subscription")

  data object EditTechnician : Screen("edit_technician/{$TECHNICIAN_ID}") {
    fun createRoute(technicianId: String?) =
      "edit_technician/${technicianId ?: "new"}"
  }

  data object EditThing : Screen("edit_thing/{$THING_ID}") {
    fun createRoute(thingId: String) = "edit_thing/$thingId"
  }

  data object ManageAccess : Screen("manage_access/{$THING_ID}") {
    fun createRoute(thingId: String) = "manage_access/$thingId"
  }

  data object AddMaintenanceTask :
    Screen("maintenance_task_create/{$THING_ID}") {
    fun createRoute(thingId: String) = "maintenance_task_create/$thingId"
  }

  data object EditMaintenanceTask :
    Screen("maintenance_task_edit/{$THING_ID}/{$CARD_ID}") {
    fun createRoute(
      thingId: String,
      cardId: String,
    ) = "maintenance_task_edit/$thingId/$cardId"
  }

  data object AddMaintenanceLog :
    Screen("maintenance_log_create/{$THING_ID}?$SQUAWK_ID={$SQUAWK_ID}&$CARD_ID={$CARD_ID}") {
    fun createRoute(
      thingId: String,
      squawkId: String? = null,
      cardId: String? = null,
    ): String {
      val base = "maintenance_log_create/$thingId"
      val params = buildList {
        if (squawkId != null) add("$SQUAWK_ID=$squawkId")
        if (cardId != null) add("$CARD_ID=$cardId")
      }
      return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }
  }

  data object EditMaintenanceLog :
    Screen("maintenance_log_edit/{$THING_ID}/{$LOG_ID}") {
    fun createRoute(
      thingId: String,
      logId: String,
    ) = "maintenance_log_edit/$thingId/$logId"
  }

  data object AddSquawk : Screen("squawk_create/{$THING_ID}") {
    fun createRoute(thingId: String) = "squawk_create/$thingId"
  }

  data object EditSquawk : Screen("squawk_edit/{$THING_ID}/{$SQUAWK_ID}") {
    fun createRoute(thingId: String, squawkId: String) =
      "squawk_edit/$thingId/$squawkId"
  }
}
