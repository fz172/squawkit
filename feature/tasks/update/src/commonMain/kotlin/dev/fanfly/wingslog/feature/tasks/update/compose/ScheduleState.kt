package dev.fanfly.wingslog.feature.tasks.update.compose

import com.squareup.wire.Instant
import dev.fanfly.wingslog.aircraft.EngineHourRule
import dev.fanfly.wingslog.aircraft.ImmediateRule
import dev.fanfly.wingslog.aircraft.InspectionRule
import dev.fanfly.wingslog.aircraft.LinkedRule
import dev.fanfly.wingslog.aircraft.MaintenanceTask
import dev.fanfly.wingslog.aircraft.TimeRule
import dev.fanfly.wingslog.core.datetime.toWireInstant
import kotlin.time.Clock

enum class ScheduleMode { TIME, HOURS, LINKED }
enum class ScheduleRecurrence { REPEATING, ONE_TIME, ASAP }
enum class ScheduleTimeUnit { DAYS, MONTHS, YEARS }

/**
 * Holds the user's schedule selections in the redesigned three-step form.
 *
 * Why: the design has implicit dependencies between fields (recurrence drives
 * preview copy, mode hides/shows interval, ASAP suppresses interval). Bundling
 * them into one immutable object keeps the screen-level state coherent and the
 * (de)serialization to InspectionRule lists in one place.
 */
data class ScheduleState(
  val mode: ScheduleMode? = null,
  val recurrence: ScheduleRecurrence? = null,
  val calValue: String = "",
  val calUnit: ScheduleTimeUnit = ScheduleTimeUnit.MONTHS,
  val hourValue: String = "",
  val linkedToId: String? = null,
) {
  /** Recurrence maps to is_one_time: only ONE_TIME is one-time; ASAP & REPEATING are not. */
  val isOneTime: Boolean get() = recurrence == ScheduleRecurrence.ONE_TIME

  fun toRules(existingTimeRuleCreationDate: Instant? = null): List<InspectionRule> {
    val now = Clock.System.now()
    val creationDate = existingTimeRuleCreationDate
      ?: toWireInstant(now.epochSeconds, now.nanosecondsOfSecond)

    return when (mode) {
      ScheduleMode.LINKED -> linkedToId?.let {
        listOf(InspectionRule(linked_rule = LinkedRule(parent_inspection_id = it)))
      } ?: emptyList()

      ScheduleMode.TIME -> {
        if (recurrence == ScheduleRecurrence.ASAP) {
          listOf(InspectionRule(immediate_rule = ImmediateRule()))
        } else {
          val n = calValue.toIntOrNull() ?: return emptyList()
          val rule = when (calUnit) {
            ScheduleTimeUnit.DAYS -> TimeRule(interval_days = n, creation_date = creationDate)
            ScheduleTimeUnit.MONTHS -> TimeRule(interval_months = n, creation_date = creationDate)
            ScheduleTimeUnit.YEARS -> TimeRule(interval_years = n, creation_date = creationDate)
          }
          listOf(InspectionRule(time_rule = rule))
        }
      }

      ScheduleMode.HOURS -> {
        if (recurrence == ScheduleRecurrence.ASAP) {
          listOf(InspectionRule(immediate_rule = ImmediateRule()))
        } else {
          val v = hourValue.toFloatOrNull() ?: return emptyList()
          listOf(InspectionRule(engine_hour_rule = EngineHourRule(interval_hours = v)))
        }
      }

      null -> emptyList()
    }
  }

  companion object {
    fun fromTask(task: MaintenanceTask): ScheduleState {
      val timeRule = task.rules.firstNotNullOfOrNull { it.time_rule }
      val engineRule = task.rules.firstNotNullOfOrNull { it.engine_hour_rule }
      val linkedRule = task.rules.firstNotNullOfOrNull { it.linked_rule }
      val immediateRule = task.rules.firstNotNullOfOrNull { it.immediate_rule }

      val baseRecurrence = when {
        immediateRule != null -> ScheduleRecurrence.ASAP
        task.is_one_time -> ScheduleRecurrence.ONE_TIME
        else -> ScheduleRecurrence.REPEATING
      }

      return when {
        linkedRule != null -> ScheduleState(
          mode = ScheduleMode.LINKED,
          recurrence = if (baseRecurrence == ScheduleRecurrence.ASAP) ScheduleRecurrence.REPEATING else baseRecurrence,
          linkedToId = linkedRule.parent_inspection_id,
        )

        timeRule != null -> {
          val (value, unit) = when {
            timeRule.interval_days > 0 -> timeRule.interval_days.toString() to ScheduleTimeUnit.DAYS
            timeRule.interval_years > 0 -> timeRule.interval_years.toString() to ScheduleTimeUnit.YEARS
            else -> timeRule.interval_months.toString() to ScheduleTimeUnit.MONTHS
          }
          ScheduleState(
            mode = ScheduleMode.TIME,
            recurrence = baseRecurrence,
            calValue = if (value == "0") "" else value,
            calUnit = unit,
          )
        }

        engineRule != null -> ScheduleState(
          mode = ScheduleMode.HOURS,
          recurrence = baseRecurrence,
          hourValue = engineRule.interval_hours.takeIf { it > 0f }?.let {
            if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString()
          } ?: "",
        )

        immediateRule != null -> {
          // Immediate without a time/hours/linked rule is rare; default to time mode for editing
          ScheduleState(mode = ScheduleMode.TIME, recurrence = ScheduleRecurrence.ASAP)
        }

        else -> ScheduleState()
      }
    }
  }
}
