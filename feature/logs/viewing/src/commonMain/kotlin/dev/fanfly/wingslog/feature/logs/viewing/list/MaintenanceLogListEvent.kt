package dev.fanfly.wingslog.feature.logs.viewing.list

import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.ThingId

sealed interface MaintenanceLogListEvent {
  /** A snackbar for the section to post: a quick action's outcome, or a failure (design §7). */
  data class ShowMessage(val message: UiText) : MaintenanceLogListEvent

  data class NavigateToCreateLog(val thingId: ThingId) :
    MaintenanceLogListEvent

  data class NavigateToEditLog(val thingId: ThingId, val logId: MaintenanceLogId) :
    MaintenanceLogListEvent
}
