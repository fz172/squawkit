package dev.fanfly.wingslog.feature.tasks.update.compose

import com.squareup.wire.Instant
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.feature.tasks.datamanager.defaultMeterKey
import dev.fanfly.wingslog.thing.ImmediateRule
import dev.fanfly.wingslog.thing.InspectionRule
import dev.fanfly.wingslog.thing.LinkedRule
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.MeterRule
import dev.fanfly.wingslog.thing.TimeRule
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
  /**
   * Which meter [hourValue] is an interval of (#759).
   *
   * Defaults to engine hours so a task written before meter rules existed edits as it always has.
   * A car's task carries "odometer" and the same field means miles.
   */
  val meterKey: String = MeterKeys.ENGINE_HOURS,
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
            ScheduleTimeUnit.DAYS -> TimeRule(
              interval_days = n,
              creation_date = creationDate
            )

            ScheduleTimeUnit.MONTHS -> TimeRule(
              interval_months = n,
              creation_date = creationDate
            )

            ScheduleTimeUnit.YEARS -> TimeRule(
              interval_years = n,
              creation_date = creationDate
            )
          }
          listOf(InspectionRule(time_rule = rule))
        }
      }

      ScheduleMode.HOURS -> {
        if (recurrence == ScheduleRecurrence.ASAP) {
          listOf(InspectionRule(immediate_rule = ImmediateRule()))
        } else {
          val v = hourValue.toFloatOrNull() ?: return emptyList()
          // A MeterRule carrying the key, which is what lets "every 5,000 miles" exist at all —
          // an EngineHourRule named its meter in its type and could only mean hours (#759).
          listOf(
            InspectionRule(
              meter_rule = MeterRule(meter_key = meterKey, interval = v),
            ),
          )
        }
      }

      null -> emptyList()
    }
  }

  companion object {
    fun fromTask(task: MaintenanceTask): ScheduleState {
      val timeRule = task.rules.firstNotNullOfOrNull { it.time_rule }
      val meterRule = task.rules.firstNotNullOfOrNull { it.meter_rule }
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

        meterRule != null -> ScheduleState(
          mode = ScheduleMode.HOURS,
          recurrence = baseRecurrence,
          hourValue = meterRule.interval
            .takeIf { it > 0f }
            ?.let {
              if (it == it.toInt()
                  .toFloat()
              ) it.toInt()
                .toString() else it.toString()
            } ?: "",
          // A rule stored without a key predates MeterRule carrying one; the default is what its
          // component always implied.
          meterKey = meterRule.meter_key.takeIf { it.isNotEmpty() }
            ?: task.defaultMeterKey(),
        )

        immediateRule != null -> {
          // Immediate without a time/hours/linked rule is rare; default to time mode for editing
          ScheduleState(
            mode = ScheduleMode.TIME,
            recurrence = ScheduleRecurrence.ASAP
          )
        }

        else -> ScheduleState()
      }
    }
  }
}
