package dev.fanfly.wingslog.feature.thing.dashboard.data

import dev.fanfly.wingslog.core.ui.common.UiText

sealed interface ThingOverviewEvent {

  data object NavigateBack : ThingOverviewEvent

  /** A snackbar for the section to post: a quick action's outcome, or a failure. */
  data class ShowMessage(val message: UiText) : ThingOverviewEvent
  data class NavigateToEditThing(val thingId: String) :
    ThingOverviewEvent

  data class NavigateToManageAccess(val thingId: String) :
    ThingOverviewEvent

  data class NavigateToAddLog(val thingId: String) : ThingOverviewEvent
  data class NavigateToEditLog(val thingId: String, val logId: String) :
    ThingOverviewEvent

  data class NavigateToAddTask(val thingId: String) : ThingOverviewEvent
  data class NavigateToEditTask(val thingId: String, val cardId: String) :
    ThingOverviewEvent

  data class NavigateToAddSquawk(val thingId: String) : ThingOverviewEvent
  data class NavigateToEditSquawk(
    val thingId: String,
    val squawkId: String
  ) : ThingOverviewEvent
}
