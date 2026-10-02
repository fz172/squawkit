package dev.fanfly.wingslog.feature.tasks.datamanager

import dev.fanfly.wingslog.core.template.SlotKeys
import dev.fanfly.wingslog.core.template.usesComponentTypes
import dev.fanfly.wingslog.task.ComplianceType
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.MeterRule
import dev.fanfly.wingslog.task.SeasonalRule
import dev.fanfly.wingslog.task.TaskOrigin
import dev.fanfly.wingslog.task.TaskOriginKind
import dev.fanfly.wingslog.task.TimeRule
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.StarterTask
import dev.fanfly.wingslog.thing.ThingTemplate
import com.squareup.wire.Instant as WireInstant

/**
 * The ordinary [MaintenanceTask] an accepted starter task becomes (PRD §4.9).
 *
 * Editable and deletable like one typed in; only its `origin` says it came from the template's pack
 * (TEMPLATE_STARTER, docs/ai/task_population_design.md §4.1), which lets analytics compare how long
 * static and AI suggestions survive. The id is left for `TaskDataManager.addTask` to assign. Both
 * halves of the pack's rule survive when both are set, so "every 5,000 mi or 6 months" is two rules
 * and the due engine takes the earlier.
 */
fun StarterTask.toMaintenanceTask(
  template: ThingTemplate?,
  createdAt: WireInstant,
): MaintenanceTask = MaintenanceTask(
  title = title,
  notes = description,
  component = componentTypeForSlot(component_slot_key, template),
  origin = TaskOrigin(kind = TaskOriginKind.TASK_ORIGIN_KIND_TEMPLATE_STARTER, suggested_at = createdAt),
  type = ComplianceType.COMPLIANCE_TYPE_ROUTINE_INSPECTION,
  rules = buildList {
    if (months.isNotEmpty()) {
      // Calendar-anchored, due by the end of each listed month (PRD §4.6).
      add(
        InspectionRule(
          seasonal_rule = SeasonalRule(
            months = months.filter { it in 1..12 }
              .distinct()
              .sorted()
          ),
        )
      )
    }
    if (interval_months > 0) {
      add(
        InspectionRule(
          time_rule = TimeRule(
            interval_months = interval_months,
            creation_date = createdAt,
            // The template's convention, the same way the form stamps it (PRD §4.6).
            due_on_anniversary =
              template?.capabilities?.month_intervals_due_on_anniversary
                ?: false,
          )
        )
      )
    }
    if (meter_key.isNotEmpty() && interval > 0f) {
      add(
        InspectionRule(
          meter_rule = MeterRule(
            meter_key = meter_key,
            interval = interval
          )
        )
      )
    }
  },
)

/**
 * The `ComponentType` a task filed against component slot [slotKey] gets, shared by starter tasks
 * and AI suggestions (design §3, §7.4). `ComponentType` is aviation's frozen enum. On the airplane
 * preset a task on the Thing itself is an airframe task — the template declares no airframe slot,
 * so an empty key is what "airframe" looks like — and every other preset files it against the Thing
 * with no component (#732).
 */
fun componentTypeForSlot(slotKey: String, template: ThingTemplate?): ComponentType =
  when {
    !template.usesComponentTypes -> ComponentType.COMPONENT_UNKNOWN
    slotKey == SlotKeys.ENGINE -> ComponentType.COMPONENT_ENGINE
    slotKey == SlotKeys.PROPELLER -> ComponentType.COMPONENT_PROPELLER
    else -> ComponentType.COMPONENT_AIRFRAME
  }
