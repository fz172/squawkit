package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import com.squareup.wire.Instant
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.core.template.MeterKeys
import dev.fanfly.wingslog.feature.tasks.datamanager.defaultMeterKey
import dev.fanfly.wingslog.feature.tasks.datamanager.forcedDueMeter
import dev.fanfly.wingslog.feature.tasks.datamanager.meterKeyFor
import dev.fanfly.wingslog.feature.tasks.datamanager.toDueDate
import dev.fanfly.wingslog.task.ImmediateRule
import dev.fanfly.wingslog.task.InspectionRule
import dev.fanfly.wingslog.task.LinkedRule
import dev.fanfly.wingslog.task.MaintenanceTask
import dev.fanfly.wingslog.task.MeterRule
import dev.fanfly.wingslog.task.SeasonalRule
import dev.fanfly.wingslog.task.TimeRule
import dev.fanfly.wingslog.thing.ComponentType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

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
  /** Calendar months (1–12) a SEASONAL schedule is anchored to — "April and October" (PRD §4.6). */
  val seasonalMonths: Set<Int> = emptySet(),
  val linkedToId: String? = null,
) {
  /** Recurrence maps to is_one_time: only ONE_TIME is one-time; ASAP & REPEATING are not. */
  val isOneTime: Boolean get() = recurrence == ScheduleRecurrence.ONE_TIME

  /**
   * [dueOnAnniversary] is the template's `month_intervals_due_on_anniversary`, stamped onto the
   * TimeRule so the due engine can honour it without a template in hand. Aviation leaves it
   * false and keeps the end-of-month convention.
   */
  fun toRules(
    existingTimeRuleCreationDate: Instant? = null,
    dueOnAnniversary: Boolean = false,
  ): List<InspectionRule> {
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
              creation_date = creationDate,
              due_on_anniversary = dueOnAnniversary,
            )

            ScheduleTimeUnit.MONTHS -> TimeRule(
              interval_months = n,
              creation_date = creationDate,
              due_on_anniversary = dueOnAnniversary,
            )

            ScheduleTimeUnit.YEARS -> TimeRule(
              interval_years = n,
              creation_date = creationDate,
              due_on_anniversary = dueOnAnniversary,
            )
          }
          listOf(InspectionRule(time_rule = rule))
        }
      }

      ScheduleMode.SEASONAL -> {
        if (seasonalMonths.isEmpty()) return emptyList()
        // Due at the end of each listed month: "in April" means by the time April is over.
        listOf(
          InspectionRule(
            seasonal_rule = SeasonalRule(
              months = seasonalMonths.sorted(),
              day_of_month = 0
            ),
          ),
        )
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

  /**
   * The meter a first due or an override is read on, for a card filed against [component] with
   * [rules]: the rules' own, else the one this schedule tracks, else the component's default. The
   * middle step is what keeps a rule-less one-time item's reading on its meter (an odometer's, say)
   * where the default knows only an aircraft's.
   */
  fun forcedDueMeterKey(component: ComponentType, rules: List<InspectionRule>): String =
    rules.firstNotNullOfOrNull { rule -> rule.meter_rule?.meter_key?.takeIf { it.isNotEmpty() } }
      ?: meterKey.takeIf { mode == ScheduleMode.HOURS && it.isNotEmpty() }
      ?: meterKeyFor(component, rules)

  companion object {
    /** 600.0 → "600", 7.5 → "7.5": a meter amount as the interval field holds it. */
    private fun Float.toIntervalText(): String =
      if (this == toInt().toFloat()) toInt().toString() else toString()

    /** [today] is what a rule-less item's due date is counted from, for its "in how long". */
    fun fromTask(
      task: MaintenanceTask,
      today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    ): ScheduleState {
      val timeRule = task.rules.firstNotNullOfOrNull { it.time_rule }
      val meterRule = task.rules.firstNotNullOfOrNull { it.meter_rule }
      val seasonalRule = task.rules.firstNotNullOfOrNull { it.seasonal_rule }
      val linkedRule = task.rules.firstNotNullOfOrNull { it.linked_rule }
      val immediateRule = task.rules.firstNotNullOfOrNull { it.immediate_rule }
      val forcedDue = task.forcedDueMeter()
      val forcedDate = task.force_due_date?.takeIf { it.getEpochSecond() > 0L }?.toDueDate()

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

        seasonalRule != null -> ScheduleState(
          mode = ScheduleMode.SEASONAL,
          recurrence = if (baseRecurrence == ScheduleRecurrence.ASAP) ScheduleRecurrence.REPEATING else baseRecurrence,
          seasonalMonths = seasonalRule.months.filter { it in 1..12 }
            .toSet(),
        )

        meterRule != null -> ScheduleState(
          mode = ScheduleMode.HOURS,
          recurrence = baseRecurrence,
          hourValue = meterRule.interval.takeIf { it > 0f }?.toIntervalText() ?: "",
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

        // No rule at all, only the point it is due at: a one-time item as a suggestion writes
        // it ("first service at 600 mi", "within 30 days"). It is tracked by whichever it is due
        // by, in the meter the reading is on, and that point is its "in how long": once, 600 mi.
        forcedDue != null -> ScheduleState(
          mode = ScheduleMode.HOURS,
          recurrence = baseRecurrence,
          hourValue = forcedDue.value.toIntervalText(),
          meterKey = forcedDue.meterKey,
        )

        forcedDate != null -> ScheduleState(
          mode = ScheduleMode.TIME,
          recurrence = baseRecurrence,
          // The days left until it; a date already here or past has no "in how long" to show.
          calValue = today.daysUntil(forcedDate).takeIf { it > 0 }?.toString().orEmpty(),
          calUnit = ScheduleTimeUnit.DAYS,
        )

        else -> ScheduleState()
      }
    }
  }
}
