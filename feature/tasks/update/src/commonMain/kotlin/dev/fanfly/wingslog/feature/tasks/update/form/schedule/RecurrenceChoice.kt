package dev.fanfly.wingslog.feature.tasks.update.form.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * Repeating / one-time / ASAP as one segmented control. The explanation each choice used to carry
 * on its own card sits under the control instead, for the choice that is made.
 */
@Composable
internal fun RecurrenceChoice(
  selected: ScheduleRecurrence?,
  options: List<RecurrenceOption>,
  onSelect: (ScheduleRecurrence) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
    SegmentedChoice(
      options = options.map {
        SegmentOption(
          it.recurrence,
          stringResource(it.label)
        )
      },
      selected = selected,
      onSelect = onSelect,
    )
    options.firstOrNull { it.recurrence == selected }
      ?.let {
        Text(
          stringResource(it.explanation),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
  }
}
