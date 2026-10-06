package dev.fanfly.wingslog.core.nav

import dev.fanfly.wingslog.core.model.id.value
import dev.fanfly.wingslog.id.DataLogId
import dev.fanfly.wingslog.id.ThingId

sealed class Screen(val route: String) {

  // Canonical navigation parameters
  companion object {
    const val THING_ID = "thingId"
    const val CARD_ID = "cardId"
    const val LOG_ID = "logId"
    const val TECHNICIAN_ID = "technicianId"
    const val SQUAWK_ID = "squawkId"
    const val TEMPLATE_ID = "templateId"
    const val SUGGESTIONS_MODE = "mode"

    /** The manuals picked on the Add Tasks sheet, for the suggestions screen's `add` mode. */
    const val SUGGESTIONS_DOCUMENT = "document"
    const val DATA_LOG_ID = "dataLogId"

    const val CROSS_SCREEN_SUCCESS_MESSAGE = "success_message"

    /**
     * Set true on the shell's entry by a dialog that a guest tapped to sign in from (the Add Tasks
     * sheet's *Suggest*): the shell opens the link-account flow it hosts once the dialog closes.
     */
    const val CROSS_SCREEN_LINK_ACCOUNT = "link_account"

    /**
     * The add-task form's draft mode (task population T18): [TASK_DRAFT] is the task it opens
     * pre-filled from, and [CROSS_SCREEN_TASK_DRAFT] the edited task it hands back, both encoded
     * by `toDraftArg`. The form writes nothing in this mode; the screen that opened it does.
     */
    const val TASK_DRAFT = "draft"
    const val CROSS_SCREEN_TASK_DRAFT = "task_draft_result"

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
   * The template's recommended schedule (PRD §4.9), and the AI suggestions that join it
   * (docs/ai/task_population_design.md §9.1). Reached from an empty Tasks tab and a finished run's
   * push (`starter`, the default), and from the Add Tasks sheet's *Suggest* (`add`, which starts the
   * model run at once with the manuals picked there, in [SUGGESTIONS_DOCUMENT]); not after creating
   * a Thing (2026-10-03).
   */
  data object StarterPack :
    Screen(
      "starter_pack/{$THING_ID}?$SUGGESTIONS_MODE={$SUGGESTIONS_MODE}" +
        "&$SUGGESTIONS_DOCUMENT={$SUGGESTIONS_DOCUMENT}"
    ) {
    /**
     * [documents]: the add mode's manuals (`toDocumentsArg`). The starter mode is the bare route, as
     * before modes existed.
     */
    fun createRoute(
      thingId: String,
      mode: SuggestionsMode = SuggestionsMode.STARTER,
      documents: String? = null,
    ) = when {
      mode == SuggestionsMode.STARTER -> "starter_pack/$thingId"
      documents == null -> "starter_pack/$thingId?$SUGGESTIONS_MODE=${mode.wire}"
      else -> "starter_pack/$thingId?$SUGGESTIONS_MODE=${mode.wire}&$SUGGESTIONS_DOCUMENT=$documents"
    }
  }

  data object EnterInviteCode : Screen("enter_invite_code")

  data object ManageTechnicians : Screen("manage_technicians")

  data object DeveloperOptions : Screen("developer_options")
  data object Subscription : Screen("subscription")
  data object About : Screen("about")

  /** The data log visualizer, a full-screen root (data log design §10.4). Ids are boxed (§4.4). */
  data object DataLogViewer : Screen("data_log/{$THING_ID}/{$DATA_LOG_ID}") {
    fun createRoute(thingId: ThingId, dataLogId: DataLogId) =
      "data_log/${thingId.value}/${dataLogId.value}"
  }

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

  /**
   * The Add Tasks sheet (a dialog on wide layouts): *Suggest* with optional manuals, or *Create
   * manually*. What the task tab's add button opens.
   */
  data object AddTasks : Screen("add_tasks/{$THING_ID}") {
    fun createRoute(thingId: String) = "add_tasks/$thingId"
  }

  data object AddMaintenanceTask :
    Screen("maintenance_task_create/{$THING_ID}?$TASK_DRAFT={$TASK_DRAFT}") {
    /** [draft]: a task to start from, in draft mode (see [TASK_DRAFT]); null for a new one. */
    fun createRoute(thingId: String, draft: String? = null) =
      if (draft == null) "maintenance_task_create/$thingId" else "maintenance_task_create/$thingId?$TASK_DRAFT=$draft"
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
