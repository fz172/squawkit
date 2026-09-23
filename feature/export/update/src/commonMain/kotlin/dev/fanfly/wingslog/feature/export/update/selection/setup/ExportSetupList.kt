package dev.fanfly.wingslog.feature.export.update.selection.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.common.compose.GroupedSection
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.export.datamanager.ExportFormat
import dev.fanfly.wingslog.feature.export.update.selection.DateRangeOption
import dev.fanfly.wingslog.feature.export.update.selection.ExportUiState
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.your_stuff
import wingslog.feature.export.sharedassets.generated.resources.Res
import wingslog.feature.export.sharedassets.generated.resources.export_clear_all
import wingslog.feature.export.sharedassets.generated.resources.export_select_all
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun ExportSetupList(
  state: ExportUiState.Configuring,
  onToggleThing: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearAll: () -> Unit,
  onToggleFormat: (ExportFormat) -> Unit,
  onDateRangeChange: (DateRangeOption) -> Unit,
  onCustomRangeChange: (LocalDate, LocalDate) -> Unit,
  modifier: Modifier = Modifier,
  bottomPadding: Dp = Spacing.screenPadding,
) {
  LazyColumn(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.extraLarge),
  ) {
    item {
      val allSelected = state.selectedThingIds.size == state.things.size
      Spacer(Modifier.height(Spacing.small))
      GroupedSection(
        // Neutral: the list spans every template on the account, so no one Thing's word
        // describes it — the same rule as the switcher's own chrome (§6).
        title = stringResource(CoreRes.string.your_stuff),
        action = if (state.things.size > 1) {
          {
            Text(
              text = stringResource(
                if (allSelected) Res.string.export_clear_all else Res.string.export_select_all
              ),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.clickable(onClick = if (allSelected) onClearAll else onSelectAll),
            )
          }
        } else {
          null
        },
      ) {
        GroupedRowGroup(
          rows = state.things.map { thing ->
            {
              ThingOptionRow(
                thing = thing,
                selected = thing.thingId in state.selectedThingIds,
                onClick = { onToggleThing(thing.thingId) },
              )
            }
          }
        )
      }
    }

    item {
      DateRangeSection(
        state = state,
        onDateRangeChange = onDateRangeChange,
        onCustomRangeChange = onCustomRangeChange,
      )
    }

    item {
      FormatSection(formats = state.formats, onToggleFormat = onToggleFormat)
    }

    item {
      Spacer(Modifier.height(bottomPadding))
    }
  }
}
