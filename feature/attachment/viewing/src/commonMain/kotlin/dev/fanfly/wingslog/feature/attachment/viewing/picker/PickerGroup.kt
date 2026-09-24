package dev.fanfly.wingslog.feature.attachment.viewing.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.form.FormSectionLabel
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun PickerGroup(
  label: String,
  options: List<PickerOption>,
  columns: Int,
  trailing: @Composable () -> Unit = {},
  footer: (@Composable () -> Unit)? = null,
) {
  val isGrid = columns > 1
  Column(
    modifier = Modifier.padding(top = Spacing.medium),
    verticalArrangement = Arrangement.spacedBy(if (isGrid) Spacing.small else Spacing.none),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = if (isGrid) Spacing.extraSmall else Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FormSectionLabel(
        text = label,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.weight(1f),
      )
      trailing()
    }
    if (isGrid) {
      options.chunked(columns)
        .forEach { row ->
          Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            row.forEach { OptionCard(it, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
          }
        }
    } else {
      options.forEach { OptionRow(it) }
    }
    footer?.invoke()
  }
}
