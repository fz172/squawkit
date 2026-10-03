package dev.fanfly.wingslog.feature.tasks.suggestions.model

import dev.fanfly.wingslog.id.SuggestionId
import dev.fanfly.wingslog.rpc.suggesttasks.TaskSuggestion
import dev.fanfly.wingslog.task.ComplianceType
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MeterRule
import dev.fanfly.wingslog.task.SeasonalRule
import dev.fanfly.wingslog.task.StarterTask
import dev.fanfly.wingslog.task.TaskOriginKind
import dev.fanfly.wingslog.task.TimeRule

/**
 * A template starter task as the curated suggestion the server would send for it (design §6.8):
 * the same rules `toMaintenanceTask` writes, so a card reads the same whichever side it came from.
 * Display only; deleted with the app's pack (T25).
 */
fun StarterTask.toSuggestion(index: Int): TaskSuggestion = TaskSuggestion(
  suggestion_id = SuggestionId(value_ = "c$index"),
  title = title,
  description = description,
  component_slot_key = component_slot_key,
  rules = buildList {
    val calendarMonths = months.filter { it in 1..12 }.distinct().sorted()
    if (calendarMonths.isNotEmpty()) add(InspectionRule(seasonal_rule = SeasonalRule(months = calendarMonths)))
    if (interval_months > 0) add(InspectionRule(time_rule = TimeRule(interval_months = interval_months)))
    if (meter_key.isNotEmpty() && interval > 0f) {
      add(InspectionRule(meter_rule = MeterRule(meter_key = meter_key, interval = interval)))
    }
  },
  type = ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
  preselect = default_selected,
  origin_kind = TaskOriginKind.TASK_ORIGIN_KIND_PRE_CURATED,
)
