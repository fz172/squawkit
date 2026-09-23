package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import org.jetbrains.compose.resources.StringResource

/** One way a task can come due, with the line that explains it once it is picked. */
internal data class RecurrenceOption(
  val recurrence: ScheduleRecurrence,
  val label: StringResource,
  val explanation: StringResource,
)
