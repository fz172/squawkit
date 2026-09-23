package dev.fanfly.wingslog.feature.logs.viewing.list

import dev.fanfly.wingslog.core.ui.text.UiText

sealed interface MaintenanceLogListEvent {
  /** A snackbar for the section to post: a quick action's outcome, or a failure (design §7). */
  data class ShowMessage(val message: UiText) : MaintenanceLogListEvent

  data class NavigateToCreateLog(val thingId: String) :
    MaintenanceLogListEvent

  data class NavigateToEditLog(val thingId: String, val logId: String) :
    MaintenanceLogListEvent
}
