package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.meterForComponent
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.thing.ComponentType
import dev.fanfly.wingslog.thing.MaintenanceTask
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.initial_due_section_label
import wingslog.feature.tasks.update.generated.resources.schedule_prefix_every
import wingslog.feature.tasks.update.generated.resources.schedule_prefix_in
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_asap
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_asap_sub
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_linked_one_time_sub
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_linked_repeating_sub
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_one_time
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_one_time_sub
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_repeating
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_repeating_sub
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_seasonal_one_time_sub
import wingslog.feature.tasks.update.generated.resources.schedule_recurrence_seasonal_repeating_sub
import wingslog.feature.tasks.update.generated.resources.schedule_step_interval_how_often
import wingslog.feature.tasks.update.generated.resources.schedule_step_interval_in_how_long
import wingslog.feature.tasks.update.generated.resources.schedule_step_months_label
import wingslog.feature.tasks.update.generated.resources.schedule_step_recurrence_label
import wingslog.feature.tasks.update.generated.resources.schedule_step_recurrence_linked_label
import wingslog.feature.tasks.update.generated.resources.schedule_step_track_label
import wingslog.feature.tasks.update.generated.resources.schedule_unit_tach_hours

@Composable
fun TaskScheduleTab(
  state: ScheduleState,
  onChange: (ScheduleState) -> Unit,
  availableInspections: List<MaintenanceTask>,
  /** What the task is filed against; on the airplane this picks the meter (engine vs prop vs airframe). */
  component: ComponentType,
  modifier: Modifier = Modifier,
  initialDue: InitialDueControls? = null,
  /** The draft's due with and without its override, for the banner both tabs share. */
  effectiveDue: DueMetadata? = null,
  naturalDue: DueMetadata? = null,
  overrideOn: Boolean = false,
  currentReading: (String) -> Float = { 0f },
) {
  var advancedOpen by remember(state.mode) { mutableStateOf(state.mode == ScheduleMode.LINKED) }

  // The meter this schedule counts in, resolved once for the mode button, the input and the
  // preview alike — they used to disagree (#785). On the airplane the component decides (an
  // engine task counts engine hours, a prop task prop hours, an airframe task airframe time);
  // elsewhere the template's one meter does. A template with none keeps the aviation word.
  val template = LocalThingTemplate.current
  val meter = template.meterForComponent(component)
  val meterUnit = meter?.unit_label?.takeIf { it.isNotEmpty() }
    ?: stringResource(Res.string.schedule_unit_tach_hours)

  Column(
    modifier = modifier.fillMaxWidth(),
    // Tight enough that the banner and all three choices sit above the fold on a phone.
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    DueSummaryBanner(
      schedule = state,
      linkedTaskName = availableInspections.firstOrNull { it.id == state.linkedToId }?.title,
      meterUnit = meterUnit,
      overrideOn = overrideOn,
      effectiveDue = effectiveDue,
      naturalDue = naturalDue,
      currentReading = currentReading,
    )

    // How is this tracked?
    ScheduleSection(
      labelRes = Res.string.schedule_step_track_label,
      complete = state.mode != null,
    ) {
      TrackingModeChoice(
        selected = if (state.mode == ScheduleMode.LINKED) null else state.mode,
        meter = meter,
        onSelect = { picked ->
          // Switching mode resets dependent fields to avoid carrying stale values
          onChange(
            state.copy(
              mode = picked,
              linkedToId = null,
              recurrence = state.recurrence?.takeIf { it != ScheduleRecurrence.ASAP || picked != ScheduleMode.LINKED },
            )
          )
        },
      )
    }

    // Recurrence (with ASAP) for time/hours; without ASAP for linked
    if (state.mode == ScheduleMode.TIME || state.mode == ScheduleMode.HOURS) {
      ScheduleSection(
        labelRes = Res.string.schedule_step_recurrence_label,
        complete = state.recurrence != null,
      ) {
        RecurrenceChoice(
          selected = state.recurrence,
          options = listOf(
            RecurrenceOption(
              ScheduleRecurrence.REPEATING,
              Res.string.schedule_recurrence_repeating,
              Res.string.schedule_recurrence_repeating_sub
            ),
            RecurrenceOption(
              ScheduleRecurrence.ONE_TIME,
              Res.string.schedule_recurrence_one_time,
              Res.string.schedule_recurrence_one_time_sub
            ),
            RecurrenceOption(
              ScheduleRecurrence.ASAP,
              Res.string.schedule_recurrence_asap,
              Res.string.schedule_recurrence_asap_sub
            ),
          ),
          onSelect = { onChange(state.copy(recurrence = it)) },
        )
      }
    } else if (state.mode == ScheduleMode.SEASONAL) {
      // No ASAP: a seasonal task is due in its months, never "now".
      ScheduleSection(
        labelRes = Res.string.schedule_step_recurrence_label,
        complete = state.recurrence != null,
      ) {
        RecurrenceChoice(
          selected = state.recurrence,
          options = listOf(
            RecurrenceOption(
              ScheduleRecurrence.REPEATING,
              Res.string.schedule_recurrence_repeating,
              Res.string.schedule_recurrence_seasonal_repeating_sub
            ),
            RecurrenceOption(
              ScheduleRecurrence.ONE_TIME,
              Res.string.schedule_recurrence_one_time,
              Res.string.schedule_recurrence_seasonal_one_time_sub
            ),
          ),
          onSelect = { onChange(state.copy(recurrence = it)) },
        )
      }
    } else if (state.mode == ScheduleMode.LINKED) {
      ScheduleSection(
        labelRes = Res.string.schedule_step_recurrence_linked_label,
        complete = state.recurrence != null,
      ) {
        RecurrenceChoice(
          selected = state.recurrence,
          options = listOf(
            RecurrenceOption(
              ScheduleRecurrence.REPEATING,
              Res.string.schedule_recurrence_repeating,
              Res.string.schedule_recurrence_linked_repeating_sub
            ),
            RecurrenceOption(
              ScheduleRecurrence.ONE_TIME,
              Res.string.schedule_recurrence_one_time,
              Res.string.schedule_recurrence_linked_one_time_sub
            ),
          ),
          onSelect = { onChange(state.copy(recurrence = it)) },
        )
      }
    }

    // Interval (hidden if ASAP or no recurrence picked)
    val showInterval =
      (state.mode == ScheduleMode.TIME || state.mode == ScheduleMode.HOURS) &&
        state.recurrence != null && state.recurrence != ScheduleRecurrence.ASAP
    if (showInterval) {
      val intervalLabel = if (state.recurrence == ScheduleRecurrence.ONE_TIME) {
        Res.string.schedule_step_interval_in_how_long
      } else {
        Res.string.schedule_step_interval_how_often
      }
      val complete = when (state.mode) {
        ScheduleMode.TIME -> state.calValue.isNotBlank()
        ScheduleMode.HOURS -> state.hourValue.isNotBlank()
        else -> false
      }
      ScheduleSection(
        labelRes = intervalLabel,
        complete = complete
      ) {
        when (state.mode) {
          ScheduleMode.TIME -> Column(
            verticalArrangement = Arrangement.spacedBy(
              Spacing.medium
            )
          ) {
            UnitPillSelect(
              selected = state.calUnit,
              onSelect = { onChange(state.copy(calUnit = it)) },
            )
            IntervalNumberInput(
              value = state.calValue,
              onChange = { onChange(state.copy(calValue = it)) },
              suffix = stringResource(state.calUnit.label()),
              prefix = stringResource(
                if (state.recurrence == ScheduleRecurrence.ONE_TIME) Res.string.schedule_prefix_in
                else Res.string.schedule_prefix_every
              ),
              keyboard = KeyboardType.Number,
            )
          }

          ScheduleMode.HOURS -> {
            // Store the resolved meter's key, so the rule counts what the button says — the
            // odometer on a car, the prop's hours for a propeller task.
            LaunchedEffect(meter?.key) {
              val key = meter?.key
              if (key != null && key != state.meterKey) {
                onChange(state.copy(meterKey = key))
              }
            }
            IntervalNumberInput(
              value = state.hourValue,
              onChange = { onChange(state.copy(hourValue = it)) },
              // The meter's own unit — "every 5,000 mi" on a car, "every 100 hrs" on an
              // aeroplane. A fixed "tach hours" was the reason a car could not express this at
              // all (#759).
              suffix = meterUnit,
              prefix = stringResource(
                if (state.recurrence == ScheduleRecurrence.ONE_TIME) Res.string.schedule_prefix_in
                else Res.string.schedule_prefix_every
              ),
              // An odometer takes no decimal point.
              keyboard = if (meter?.decimal != false) {
                KeyboardType.Decimal
              } else {
                KeyboardType.Number
              },
            )
          }

          else -> Unit
        }
      }
    }

    // A seasonal schedule's months.
    if (state.mode == ScheduleMode.SEASONAL && state.recurrence != null) {
      ScheduleSection(
        labelRes = Res.string.schedule_step_months_label,
        complete = state.seasonalMonths.isNotEmpty(),
      ) {
        MonthGrid(
          selected = state.seasonalMonths,
          onToggle = { month ->
            onChange(
              state.copy(
                seasonalMonths = if (month in state.seasonalMonths) state.seasonalMonths - month
                else state.seasonalMonths + month,
              )
            )
          },
        )
      }
    }

    // First due — create only, and only once the schedule it overrides exists. No switch: the
    // field is there, and leaving it empty means the schedule counts from today.
    if (initialDue != null && state.isComplete) {
      val set = if (state.isDated) initialDue.forcedDateMillis != null
      else initialDue.forcedEngineHours.isNotBlank()
      ScheduleSection(
        labelRes = Res.string.initial_due_section_label,
        complete = set,
      ) {
        FirstDueCard(
          dated = state.isDated,
          controls = initialDue,
          meter = meter,
          meterUnit = meterUnit,
        )
      }
    }

    AdvancedLinkedSection(
      open = advancedOpen,
      onToggle = { advancedOpen = !advancedOpen },
      isLinkedMode = state.mode == ScheduleMode.LINKED,
      linkedTask = availableInspections.firstOrNull { it.id == state.linkedToId },
      availableInspections = availableInspections,
      onPick = { picked ->
        onChange(
          state.copy(
            mode = ScheduleMode.LINKED,
            linkedToId = picked.id,
            recurrence = state.recurrence?.takeIf { it != ScheduleRecurrence.ASAP }
              ?: ScheduleRecurrence.REPEATING,
            // Clear interval values when switching to linked
            calValue = "",
            hourValue = "",
          )
        )
      },
      onClear = {
        onChange(
          state.copy(
            mode = null,
            linkedToId = null
          )
        )
      },
    )
  }
}

/** TIME and SEASONAL schedules are dated; a first due for them is a date. */
private val ScheduleState.isDated: Boolean
  get() = mode == ScheduleMode.TIME || mode == ScheduleMode.SEASONAL

/** Enough of a schedule to have a first due: an interval, a meter value, or months — and not ASAP. */
private val ScheduleState.isComplete: Boolean
  get() = recurrence != null && recurrence != ScheduleRecurrence.ASAP && when (mode) {
    ScheduleMode.TIME -> calValue.toIntOrNull() != null
    ScheduleMode.HOURS -> hourValue.toFloatOrNull() != null
    ScheduleMode.SEASONAL -> seasonalMonths.isNotEmpty()
    else -> false
  }
