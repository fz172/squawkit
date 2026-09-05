package dev.fanfly.wingslog.feature.tasks.update.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.template.LocalThingTemplate
import dev.fanfly.wingslog.core.template.meter
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.tasks.model.DueMetadata
import dev.fanfly.wingslog.thing.MaintenanceTask
import dev.fanfly.wingslog.thing.MeterDef
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.core.sharedassets.generated.resources.select_date
import wingslog.feature.tasks.update.generated.resources.Res
import wingslog.feature.tasks.update.generated.resources.adj_reschedule_prefix_at
import wingslog.feature.tasks.update.generated.resources.initial_due_section_label
import wingslog.feature.tasks.update.generated.resources.initial_due_subtitle
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

/**
 * The create form's "First due" controls — the force-due override, offered once, at creation.
 *
 * Absent on edit: an existing task reschedules from the Adjustments tab, where the banner can
 * show what the schedule currently says next to what the user is changing it to.
 */
data class InitialDueControls(
  val forceOverrideDate: Boolean,
  val onForceOverrideDateChange: (Boolean) -> Unit,
  val forcedDateMillis: Long?,
  val onForcedDateMillisChange: (Long?) -> Unit,
  val onDateClick: () -> Unit,
  val forceOverrideEngine: Boolean,
  val onForceOverrideEngineChange: (Boolean) -> Unit,
  val forcedEngineHours: String,
  val onForcedEngineHoursChange: (String) -> Unit,
)

@Composable
fun TaskScheduleTab(
  state: ScheduleState,
  onChange: (ScheduleState) -> Unit,
  availableInspections: List<MaintenanceTask>,
  modifier: Modifier = Modifier,
  initialDue: InitialDueControls? = null,
  /** The draft's due with and without its override, for the banner both tabs share. */
  effectiveDue: DueMetadata? = null,
  naturalDue: DueMetadata? = null,
  overrideOn: Boolean = false,
  currentReading: (String) -> Float = { 0f },
) {
  var advancedOpen by remember(state.mode) { mutableStateOf(state.mode == ScheduleMode.LINKED) }

  // The meter this schedule counts in, resolved once for the input and the preview alike — the
  // two used to disagree, the input saying "mi" beside a banner saying "tach hrs" (#785). A new
  // task carries the default engine-hours key, which a car's template never declares, so fall
  // back to the template's first meter; a template with none keeps the aviation word.
  val template = LocalThingTemplate.current
  val meter = template.meter(state.meterKey) ?: template?.meters?.firstOrNull()
  val meterUnit = meter?.unit_label?.takeIf { it.isNotEmpty() }
    ?: stringResource(Res.string.schedule_unit_tach_hours)

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
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

    // Step 1 — How is this tracked?
    ScheduleSection(
      labelRes = Res.string.schedule_step_track_label,
      complete = state.mode != null,
    ) {
      TrackingModeChoice(
        selected = if (state.mode == ScheduleMode.LINKED) null else state.mode,
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

    // Step 2 — Recurrence (with ASAP) for time/hours; without ASAP for linked
    if (state.mode == ScheduleMode.TIME || state.mode == ScheduleMode.HOURS) {
      ScheduleSection(
        labelRes = Res.string.schedule_step_recurrence_label,
        complete = state.recurrence != null,
      ) {
        RecurrenceChoice(
          selected = state.recurrence,
          options = listOf(
            ScheduleRecurrence.REPEATING to (Res.string.schedule_recurrence_repeating to Res.string.schedule_recurrence_repeating_sub),
            ScheduleRecurrence.ONE_TIME to (Res.string.schedule_recurrence_one_time to Res.string.schedule_recurrence_one_time_sub),
            ScheduleRecurrence.ASAP to (Res.string.schedule_recurrence_asap to Res.string.schedule_recurrence_asap_sub),
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
            ScheduleRecurrence.REPEATING to (Res.string.schedule_recurrence_repeating to Res.string.schedule_recurrence_seasonal_repeating_sub),
            ScheduleRecurrence.ONE_TIME to (Res.string.schedule_recurrence_one_time to Res.string.schedule_recurrence_seasonal_one_time_sub),
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
            ScheduleRecurrence.REPEATING to (Res.string.schedule_recurrence_repeating to Res.string.schedule_recurrence_linked_repeating_sub),
            ScheduleRecurrence.ONE_TIME to (Res.string.schedule_recurrence_one_time to Res.string.schedule_recurrence_linked_one_time_sub),
          ),
          onSelect = { onChange(state.copy(recurrence = it)) },
        )
      }
    }

    // Step 3 — Interval (hidden if ASAP or no recurrence picked)
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
            // Store the resolved meter's key, so an automotive task schedules on the odometer
            // rather than on the engine-hours default its template never declares.
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

    // Step 3 for a seasonal schedule — which months.
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

// ─── Section frame ──────────────────────────────────────────────────────────

@Composable
private fun ScheduleSection(
  labelRes: StringResource,
  complete: Boolean,
  content: @Composable () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      modifier = Modifier.padding(bottom = Spacing.small),
    ) {
      if (complete) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = null,
          modifier = Modifier.size(Spacing.large),
          tint = MaterialTheme.colorScheme.primary,
        )
      }
      Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (complete) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    content()
  }
}

/**
 * The first-due field: a date row for a dated schedule, a meter reading for a metered one, and a
 * clear affordance once something is set. Setting a value is what turns the override on — there
 * is no switch to remember to flip, and an empty field is simply no override.
 */
@Composable
private fun FirstDueCard(
  dated: Boolean,
  controls: InitialDueControls,
  meter: MeterDef?,
  meterUnit: String,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        Spacing.hairline,
        MaterialTheme.colorScheme.outlineVariant,
        RoundedCornerShape(Spacing.cardCornerRadius)
      )
      .padding(horizontal = Spacing.large, vertical = Spacing.medium),
    verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    if (dated) {
      val dateStr = controls.forcedDateMillis?.let {
        Instant.fromEpochMilliseconds(it)
          .toLocalDateTime(TimeZone.currentSystemDefault()).date.toDisplayFormat()
      }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(Spacing.cardCornerRadius))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest)
          .clickable(role = Role.Button) { controls.onDateClick() }
          .padding(horizontal = Spacing.medium, vertical = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        Icon(
          Icons.Default.CalendarToday,
          contentDescription = null,
          modifier = Modifier.size(Spacing.large),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          dateStr ?: stringResource(CoreRes.string.select_date),
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = if (dateStr != null) FontWeight.Bold else FontWeight.Normal,
          color = if (dateStr != null) MaterialTheme.colorScheme.onSurface
          else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f),
        )
        if (dateStr != null) {
          Icon(
            Icons.Default.Close,
            contentDescription = stringResource(CoreRes.string.remove),
            modifier = Modifier
              .size(Spacing.xLarge)
              .clip(RoundedCornerShape(Spacing.smallCornerRadius))
              .clickable {
                controls.onForcedDateMillisChange(null)
                controls.onForceOverrideDateChange(false)
              }
              .padding(Spacing.extraSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      IntervalNumberInput(
        value = controls.forcedEngineHours,
        onChange = { v ->
          val filtered = v.filter { c -> c.isDigit() || c == '.' }
          controls.onForcedEngineHoursChange(filtered)
          controls.onForceOverrideEngineChange(filtered.toFloatOrNull()?.let { it > 0f } == true)
        },
        suffix = meterUnit,
        prefix = stringResource(Res.string.adj_reschedule_prefix_at),
        keyboard = if (meter?.decimal != false) KeyboardType.Decimal else KeyboardType.Number,
      )
    }
    Text(
      stringResource(Res.string.initial_due_subtitle),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
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


