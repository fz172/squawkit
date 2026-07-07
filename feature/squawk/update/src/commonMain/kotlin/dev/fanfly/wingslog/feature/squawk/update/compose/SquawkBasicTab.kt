package dev.fanfly.wingslog.feature.squawk.update.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.common.compose.FormKeyboard
import dev.fanfly.wingslog.core.ui.common.compose.FormSectionLabel
import dev.fanfly.wingslog.core.ui.common.compose.FormTextField
import dev.fanfly.wingslog.core.ui.common.compose.FormValueField
import dev.fanfly.wingslog.core.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.priority_aog
import wingslog.feature.squawk.sharedassets.generated.resources.priority_high
import wingslog.feature.squawk.sharedassets.generated.resources.priority_low
import wingslog.feature.squawk.sharedassets.generated.resources.priority_medium
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_priority_label
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_reported_on
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_title_label
import wingslog.feature.squawk.sharedassets.generated.resources.squawk_title_required
import wingslog.feature.squawk.update.generated.resources.squawk_basic_tab_note
import kotlin.time.Clock
import wingslog.feature.squawk.update.generated.resources.Res as UpdateRes

@Composable
fun SquawkBasicTab(
  title: String,
  onTitleChange: (String) -> Unit,
  priority: SquawkPriority,
  onPriorityChange: (SquawkPriority) -> Unit,
  reportedDateFormatted: String,
  readOnly: Boolean,
  titleError: Boolean,
  modifier: Modifier = Modifier,
) {
  val displayDate = reportedDateFormatted.ifEmpty {
    remember {
      Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date.toDisplayFormat()
    }
  }
  val priorities = listOf(
    SquawkPriority.SQUAWK_PRIORITY_LOW to stringResource(Res.string.priority_low),
    SquawkPriority.SQUAWK_PRIORITY_MEDIUM to stringResource(Res.string.priority_medium),
    SquawkPriority.SQUAWK_PRIORITY_HIGH to stringResource(Res.string.priority_high),
    SquawkPriority.SQUAWK_PRIORITY_AOG to stringResource(Res.string.priority_aog),
  )

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    FormValueField(
      value = displayDate,
      label = stringResource(Res.string.squawk_reported_on),
      modifier = Modifier.fillMaxWidth(),
    )

    FormTextField(
      value = title,
      onValueChange = onTitleChange,
      label = stringResource(Res.string.squawk_title_label),
      isError = titleError,
      supportingText = if (titleError) stringResource(Res.string.squawk_title_required) else null,
      modifier = Modifier.fillMaxWidth(),
      keyboardOptions = FormKeyboard.SentencesDone,
      editable = !readOnly,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
      FormSectionLabel(stringResource(Res.string.squawk_priority_label))
      if (readOnly) {
        Text(
          text = priorities.first { it.first == priority }.second,
        )
      } else {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          priorities.forEachIndexed { index, (p, label) ->
            SegmentedButton(
              selected = priority == p,
              onClick = { onPriorityChange(p) },
              shape = SegmentedButtonDefaults.itemShape(
                index = index,
                count = priorities.size
              ),
            ) { Text(label) }
          }
        }
      }
      if (!readOnly) {
        Text(
          text = stringResource(UpdateRes.string.squawk_basic_tab_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
