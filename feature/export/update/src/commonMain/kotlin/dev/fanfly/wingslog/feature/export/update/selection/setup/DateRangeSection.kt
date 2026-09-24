package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.grouped.GroupedSection
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.export.update.selection.DateRangeOption
import dev.fanfly.wingslog.feature.export.update.selection.ExportUiState
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_all_time
import wingslog.feature.export.sharedassets.generated.resources.export_custom
import wingslog.feature.export.sharedassets.generated.resources.export_date_range_section
import wingslog.feature.export.sharedassets.generated.resources.export_last_12_months

@Composable
internal fun DateRangeSection(
  state: ExportUiState.Configuring,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomRangeChange: (LocalDate, LocalDate) -> Unit,
) {
  GroupedSection(title = stringResource(Res.string.export_date_range_section)) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      RangePill(
        label = stringResource(Res.string.export_all_time),
        selected = state.dateRange == DateRangeOption.AllTime,
        onClick = { onDateRangeChange(DateRangeOption.AllTime) },
      )
      RangePill(
        label = stringResource(Res.string.export_last_12_months),
        selected = state.dateRange == DateRangeOption.Last12Months,
        onClick = { onDateRangeChange(DateRangeOption.Last12Months) },
      )
      RangePill(
        label = stringResource(Res.string.export_custom),
        selected = state.dateRange == DateRangeOption.Custom,
        onClick = { onDateRangeChange(DateRangeOption.Custom) },
      )
    }
    if (state.dateRange == DateRangeOption.Custom) {
      Spacer(Modifier.height(Spacing.medium))
      CombinedRangeField(
        start = state.customStart,
        end = state.customEnd,
        onChange = onCustomRangeChange,
      )
    }
  }
}
