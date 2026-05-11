package dev.fanfly.wingslog.feature.squawk.update.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.theme.Spacing
import kotlin.time.Clock
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
  val displayDate = if (reportedDateFormatted.isNotEmpty()) {
    reportedDateFormatted
  } else {
    remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toDisplayFormat() }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    // Reported date (read-only)
    OutlinedTextField(
      value = displayDate,
      onValueChange = {},
      label = { Text(stringResource(Res.string.squawk_reported_on)) },
      enabled = false,
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    // Title
    OutlinedTextField(
      value = title,
      onValueChange = onTitleChange,
      label = { Text(stringResource(Res.string.squawk_title_label)) },
      isError = titleError,
      supportingText = if (titleError) {
        { Text(stringResource(Res.string.squawk_title_required)) }
      } else null,
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
      readOnly = readOnly,
    )

    // Priority
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
      Text(
        text = stringResource(Res.string.squawk_priority_label),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val priorities = listOf(
        SquawkPriority.SQUAWK_PRIORITY_LOW    to stringResource(Res.string.priority_low),
        SquawkPriority.SQUAWK_PRIORITY_MEDIUM to stringResource(Res.string.priority_medium),
        SquawkPriority.SQUAWK_PRIORITY_HIGH   to stringResource(Res.string.priority_high),
        SquawkPriority.SQUAWK_PRIORITY_AOG    to stringResource(Res.string.priority_aog),
      )
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        priorities.forEachIndexed { index, (p, label) ->
          SegmentedButton(
            selected = priority == p,
            onClick = { if (!readOnly) onPriorityChange(p) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = priorities.size),
            enabled = !readOnly,
          ) { Text(label) }
        }
      }
    }
  }
}
