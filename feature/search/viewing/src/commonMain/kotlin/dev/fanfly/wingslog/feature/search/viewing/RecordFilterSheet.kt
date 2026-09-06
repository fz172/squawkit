package dev.fanfly.wingslog.feature.search.viewing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.DatePickerDialog
import dev.fanfly.wingslog.core.ui.common.compose.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.thing.ComponentType
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.done
import wingslog.core.sharedassets.generated.resources.ok
import wingslog.feature.search.sharedassets.generated.resources.Res
import wingslog.feature.search.sharedassets.generated.resources.clear
import wingslog.feature.search.sharedassets.generated.resources.custom_from
import wingslog.feature.search.sharedassets.generated.resources.custom_to
import wingslog.feature.search.sharedassets.generated.resources.filter_component
import wingslog.feature.search.sharedassets.generated.resources.filter_due_within
import wingslog.feature.search.sharedassets.generated.resources.filter_period
import wingslog.feature.search.sharedassets.generated.resources.filter_scope_note

private val COMPONENT_OPTIONS = listOf(
  ComponentType.COMPONENT_AIRFRAME,
  ComponentType.COMPONENT_ENGINE,
  ComponentType.COMPONENT_PROPELLER,
  ComponentType.COMPONENT_UNKNOWN,
)

/** One sheet for every tab: component, period, then the tab’s own [facetSection]. Choices apply immediately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFilterSheet(
  title: String,
  filter: RecordFilter,
  showComponentFilter: Boolean,
  componentLabel: @Composable (ComponentType) -> String,
  onComponentToggle: (ComponentType) -> Unit,
  onTimeWindowChange: (TimeWindow) -> Unit,
  onClear: () -> Unit,
  onDismiss: () -> Unit,
  dueWithin: Boolean = false,
  timeNote: String? = null,
  facetSection: (@Composable ColumnScope.() -> Unit)? = null,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
          stringResource(Res.string.filter_scope_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (showComponentFilter) {
        FilterSection(stringResource(Res.string.filter_component)) {
          COMPONENT_OPTIONS.forEach { component ->
            ChoiceChip(
              label = componentLabel(component),
              selected = component in filter.components,
              onClick = { onComponentToggle(component) },
            )
          }
        }
      }

      TimeSection(
        time = filter.time,
        dueWithin = dueWithin,
        note = timeNote,
        onTimeWindowChange = onTimeWindowChange,
      )

      facetSection?.invoke(this)

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onClear) { Text(stringResource(Res.string.clear)) }
        Spacer(Modifier.weight(1f))
        Button(onClick = onDismiss) { Text(stringResource(CoreRes.string.done)) }
      }
      Spacer(Modifier.height(Spacing.large))
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSection(
  label: String,
  note: String? = null,
  chips: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
      label.uppercase(),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) { chips() }
    if (note != null) {
      Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label) },
    leadingIcon = if (selected) {
      { Icon(Icons.Default.Check, contentDescription = null) }
    } else null,
    shape = RoundedCornerShape(Spacing.smallCornerRadius),
  )
}

@Composable
private fun TimeSection(
  time: TimeWindow,
  dueWithin: Boolean,
  note: String?,
  onTimeWindowChange: (TimeWindow) -> Unit,
) {
  val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
  val presets = listOf(TimeWindow.All, TimeWindow.LastMonths(3), TimeWindow.LastMonths(12))
  val custom = time as? TimeWindow.Custom
  FilterSection(
    label = stringResource(if (dueWithin) Res.string.filter_due_within else Res.string.filter_period),
    note = note,
  ) {
    presets.forEach { preset ->
      ChoiceChip(
        label = preset.optionLabel(dueWithin),
        selected = time == preset,
        onClick = { onTimeWindowChange(preset) },
      )
    }
    ChoiceChip(
      label = TimeWindow.Custom(today, today).optionLabel(dueWithin),
      selected = custom != null,
      onClick = {
        if (custom == null) onTimeWindowChange(TimeWindow.Custom(today.minus(3, DateTimeUnit.MONTH), today))
      },
    )
  }
  if (custom != null) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      DateField(
        label = stringResource(Res.string.custom_from),
        date = custom.start,
        onDateChange = { onTimeWindowChange(custom.copy(start = it, end = maxOf(custom.end, it))) },
        modifier = Modifier.weight(1f),
      )
      DateField(
        label = stringResource(Res.string.custom_to),
        date = custom.end,
        onDateChange = { onTimeWindowChange(custom.copy(start = minOf(custom.start, it), end = it)) },
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
  label: String,
  date: LocalDate,
  onDateChange: (LocalDate) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showPicker by remember { mutableStateOf(false) }
  OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
    Text("$label: ${date.toDisplayFormat()}")
  }
  if (showPicker) {
    val state = rememberDatePickerState(
      initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
    )
    DatePickerDialog(
      onDismissRequest = { showPicker = false },
      confirmButton = {
        TextButton(onClick = {
          state.selectedDateMillis?.let {
            onDateChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
          }
          showPicker = false
        }) { Text(stringResource(CoreRes.string.ok)) }
      },
      dismissButton = {
        TextButton(onClick = { showPicker = false }) { Text(stringResource(CoreRes.string.cancel)) }
      },
    ) {
      DatePicker(state = state)
    }
  }
}
