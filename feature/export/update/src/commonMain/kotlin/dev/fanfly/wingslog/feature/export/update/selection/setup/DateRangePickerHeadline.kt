package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import dev.fanfly.wingslog.core.datetime.toDisplayFormat
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_custom_end_date
import wingslog.feature.export.sharedassets.generated.resources.export_custom_start_date

@Composable
internal fun DateRangePickerHeadline(
  start: LocalDate,
  end: LocalDate,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Spacing.extraLarge)
      .padding(top = Spacing.medium, bottom = Spacing.large),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    DateRangePickerHeadlineCell(
      label = stringResource(Res.string.export_custom_start_date),
      value = start.toDisplayFormat(),
      modifier = Modifier.weight(1f),
    )
    DateRangePickerHeadlineCell(
      label = stringResource(Res.string.export_custom_end_date),
      value = end.toDisplayFormat(),
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun DateRangePickerHeadlineCell(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .border(
        width = Spacing.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.cardCornerRadius),
      )
      .padding(horizontal = Spacing.medium, vertical = Spacing.small),
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = value,
      style = WingslogTypography.dataMedium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Clip,
    )
  }
}
