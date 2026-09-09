package dev.fanfly.wingslog.feature.search.viewing

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.DatePickerDialog
import dev.fanfly.wingslog.core.ui.common.compose.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.thing.ComponentType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.core.sharedassets.generated.resources.ok
import wingslog.feature.search.sharedassets.generated.resources.Res
import wingslog.feature.search.sharedassets.generated.resources.clear_all
import wingslog.feature.search.sharedassets.generated.resources.custom_from
import wingslog.feature.search.sharedassets.generated.resources.custom_to
import wingslog.feature.search.sharedassets.generated.resources.filter_close
import wingslog.feature.search.sharedassets.generated.resources.filter_pick_any
import wingslog.feature.search.sharedassets.generated.resources.filter_pick_one
import wingslog.feature.search.sharedassets.generated.resources.filter_scope_note
import wingslog.feature.search.sharedassets.generated.resources.filter_sheet_title
import wingslog.feature.search.sharedassets.generated.resources.filter_show_all
import wingslog.feature.search.sharedassets.generated.resources.filter_show_count
import wingslog.feature.search.sharedassets.generated.resources.filter_show_none
import kotlin.time.Clock
import kotlin.time.Instant
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

private val COMPONENT_OPTIONS = listOf(
  ComponentType.COMPONENT_AIRFRAME,
  ComponentType.COMPONENT_ENGINE,
  ComponentType.COMPONENT_PROPELLER,
  ComponentType.COMPONENT_UNKNOWN,
)

/** Opens the filters: an inline panel under the bar on wide tiers, a bottom sheet on compact. */
@Composable
fun RecordFilterControls(
  expanded: Boolean,
  inline: Boolean,
  /** What the sheet reaches, in the thing's own words: "open squawks", "work logs". */
  scopeLabel: String,
  filter: RecordFilter,
  showComponentFilter: Boolean,
  /** The question this section answers; the Tasks tab asks about the thing, Logs about the work. */
  componentQuestion: String,
  componentLabel: @Composable (ComponentType) -> String,
  onComponentToggle: (ComponentType) -> Unit,
  timeQuestion: String,
  onTimeWindowChange: (TimeWindow) -> Unit,
  onClear: () -> Unit,
  onDismiss: () -> Unit,
  /** How many records the current filter leaves, and how many there are in total. */
  resultCount: Int,
  totalCount: Int,
  nounSingular: String,
  nounPlural: String,
  /** Records carrying each component, for the count beside its chip; null hides every count. */
  componentCount: ((ComponentType) -> Int)? = null,
  timeCount: ((TimeWindow) -> Int)? = null,
  dueWithin: Boolean = false,
  timeNote: String? = null,
  horizontalPadding: Dp = Spacing.screenPadding,
  facetSection: (@Composable ColumnScope.() -> Unit)? = null,
) {
  if (!expanded) return
  val content: @Composable ColumnScope.() -> Unit = {
    RecordFilterPanelContent(
      scopeLabel = scopeLabel,
      filter = filter,
      showComponentFilter = showComponentFilter,
      componentQuestion = componentQuestion,
      componentLabel = componentLabel,
      onComponentToggle = onComponentToggle,
      timeQuestion = timeQuestion,
      onTimeWindowChange = onTimeWindowChange,
      onClear = onClear,
      onDone = onDismiss,
      resultCount = resultCount,
      totalCount = totalCount,
      nounSingular = nounSingular,
      nounPlural = nounPlural,
      componentCount = componentCount,
      timeCount = timeCount,
      dueWithin = dueWithin,
      timeNote = timeNote,
      facetSection = facetSection,
    )
  }
  if (inline) RecordFilterPanel(
    horizontalPadding,
    content
  ) else RecordFilterSheet(onDismiss, content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordFilterSheet(
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth()
        .padding(horizontal = Spacing.xLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      content()
      Spacer(Modifier.height(Spacing.large))
    }
  }
}

@Composable
private fun RecordFilterPanel(
  horizontalPadding: Dp,
  content: @Composable ColumnScope.() -> Unit
) {
  Surface(
    shape = RoundedCornerShape(Spacing.smallCornerRadius),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    modifier = Modifier
      .fillMaxWidth()
      .padding(
        start = horizontalPadding,
        end = horizontalPadding,
        bottom = Spacing.small
      ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth()
        .padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
      content = content,
    )
  }
}

/**
 * The sheet, in plain language: each section asks the question the pilot is actually answering,
 * says whether the options are exclusive, carries the count each one would leave, and ends in a
 * button that states its own result. Choices apply immediately; the button only closes.
 */
@Composable
private fun RecordFilterPanelContent(
  scopeLabel: String,
  filter: RecordFilter,
  showComponentFilter: Boolean,
  componentQuestion: String,
  componentLabel: @Composable (ComponentType) -> String,
  onComponentToggle: (ComponentType) -> Unit,
  timeQuestion: String,
  onTimeWindowChange: (TimeWindow) -> Unit,
  onClear: () -> Unit,
  onDone: () -> Unit,
  resultCount: Int,
  totalCount: Int,
  nounSingular: String,
  nounPlural: String,
  componentCount: ((ComponentType) -> Int)?,
  timeCount: ((TimeWindow) -> Int)?,
  dueWithin: Boolean,
  timeNote: String?,
  facetSection: (@Composable ColumnScope.() -> Unit)?,
) {
  Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        stringResource(Res.string.filter_sheet_title),
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        stringResource(Res.string.filter_scope_note, scopeLabel),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    IconButton(onClick = onDone) {
      Icon(
        Icons.Default.Close,
        contentDescription = stringResource(Res.string.filter_close),
      )
    }
  }

  if (showComponentFilter) {
    FilterSection(componentQuestion, pickOne = false) {
      COMPONENT_OPTIONS.forEach { component ->
        ChoiceChip(
          label = componentLabel(component),
          selected = component in filter.components,
          count = componentCount?.invoke(component),
          onClick = { onComponentToggle(component) },
        )
      }
    }
  }

  TimeSection(
    question = timeQuestion,
    time = filter.time,
    dueWithin = dueWithin,
    note = timeNote,
    count = timeCount,
    onTimeWindowChange = onTimeWindowChange,
  )

  facetSection?.let { Column(content = it) }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Greyed rather than hidden: its absence would be one more thing to notice.
    TextButton(onClick = onClear, enabled = filter.isActive) {
      Text(stringResource(Res.string.clear_all))
    }
    Spacer(Modifier.weight(1f))
    Button(onClick = onDone) {
      Text(
        when {
          !filter.isActive -> stringResource(
            Res.string.filter_show_all,
            totalCount,
            nounPlural
          )

          resultCount == 0 -> stringResource(
            Res.string.filter_show_none,
            nounPlural
          )

          else -> stringResource(
            Res.string.filter_show_count,
            resultCount,
            if (resultCount == 1) nounSingular else nounPlural,
          )
        }
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSection(
  /** A question, not a field name: "How urgent", not "PRIORITY". */
  label: String,
  pickOne: Boolean,
  note: String? = null,
  chips: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      verticalAlignment = Alignment.Bottom,
    ) {
      Text(label, style = MaterialTheme.typography.titleSmall)
      Text(
        stringResource(if (pickOne) Res.string.filter_pick_one else Res.string.filter_pick_any),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) { chips() }
    if (note != null) {
      Text(
        note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/**
 * A chip, with what it would leave behind. [count] is the number of records carrying this option,
 * so a chip that would empty the list says so before it is tapped; null hides the number for a
 * caller that cannot compute one.
 */
@Composable
fun ChoiceChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  count: Int? = null,
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(label)
        if (count != null) {
          Text(
            count.toString(),
            style = WingslogTypography.dataSmall,
            color = if (selected) {
              MaterialTheme.colorScheme.onSecondaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
          )
        }
      }
    },
    leadingIcon = if (selected) {
      { Icon(Icons.Default.Check, contentDescription = null) }
    } else null,
    shape = RoundedCornerShape(Spacing.smallCornerRadius),
  )
}

@Composable
private fun TimeSection(
  question: String,
  time: TimeWindow,
  dueWithin: Boolean,
  note: String?,
  count: ((TimeWindow) -> Int)?,
  onTimeWindowChange: (TimeWindow) -> Unit,
) {
  val today = remember {
    Clock.System.now()
      .toLocalDateTime(TimeZone.currentSystemDefault()).date
  }
  val presets =
    listOf(TimeWindow.All, TimeWindow.LastMonths(3), TimeWindow.LastMonths(12))
  val custom = time as? TimeWindow.Custom
  FilterSection(label = question, pickOne = true, note = note) {
    presets.forEach { preset ->
      ChoiceChip(
        label = preset.optionLabel(dueWithin),
        selected = time == preset,
        count = count?.invoke(preset),
        onClick = { onTimeWindowChange(preset) },
      )
    }
    ChoiceChip(
      label = TimeWindow.Custom(today, today)
        .optionLabel(dueWithin),
      selected = custom != null,
      onClick = {
        if (custom == null) onTimeWindowChange(
          TimeWindow.Custom(
            today.minus(
              3,
              DateTimeUnit.MONTH
            ), today
          )
        )
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
        onDateChange = {
          onTimeWindowChange(
            custom.copy(
              start = it,
              end = maxOf(
                custom.end,
                it
              )
            )
          )
        },
        modifier = Modifier.weight(1f),
      )
      DateField(
        label = stringResource(Res.string.custom_to),
        date = custom.end,
        onDateChange = {
          onTimeWindowChange(
            custom.copy(
              start = minOf(
                custom.start,
                it
              ), end = it
            )
          )
        },
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
      initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC)
        .toEpochMilliseconds(),
    )
    DatePickerDialog(
      onDismissRequest = { showPicker = false },
      confirmButton = {
        TextButton(onClick = {
          state.selectedDateMillis?.let {
            onDateChange(
              Instant.fromEpochMilliseconds(it)
                .toLocalDateTime(TimeZone.UTC).date
            )
          }
          showPicker = false
        }) { Text(stringResource(CoreRes.string.ok)) }
      },
      dismissButton = {
        TextButton(onClick = { showPicker = false }) {
          Text(
            stringResource(
              CoreRes.string.cancel
            )
          )
        }
      },
    ) {
      DatePicker(state = state)
    }
  }
}
