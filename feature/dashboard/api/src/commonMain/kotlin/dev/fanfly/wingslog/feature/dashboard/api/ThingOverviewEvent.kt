package dev.fanfly.wingslog.feature.dashboard.api

import dev.fanfly.wingslog.core.ui.text.UiText
import dev.fanfly.wingslog.id.MaintenanceLogId
import dev.fanfly.wingslog.id.MaintenanceTaskId
import dev.fanfly.wingslog.id.SquawkId
import dev.fanfly.wingslog.id.ThingId

sealed interface ThingOverviewEvent {

  data object NavigateBack : ThingOverviewEvent

  /** A snackbar for the section to post: a quick action's outcome, or a failure. */
  data class ShowMessage(val message: UiText) : ThingOverviewEvent
  data class NavigateToEditThing(val thingId: ThingId) :
    ThingOverviewEvent

  data class NavigateToManageAccess(val thingId: ThingId) :
    ThingOverviewEvent

  data class NavigateToAddLog(val thingId: ThingId) : ThingOverviewEvent
  data class NavigateToEditLog(val thingId: ThingId, val logId: MaintenanceLogId) :
    ThingOverviewEvent

  data class NavigateToAddTask(val thingId: ThingId) : ThingOverviewEvent
  data class NavigateToEditTask(val thingId: ThingId, val cardId: MaintenanceTaskId) :
    ThingOverviewEvent

  data class NavigateToAddSquawk(val thingId: ThingId) : ThingOverviewEvent
  data class NavigateToEditSquawk(
    val thingId: ThingId,
    val squawkId: SquawkId,
  ) : ThingOverviewEvent
}
