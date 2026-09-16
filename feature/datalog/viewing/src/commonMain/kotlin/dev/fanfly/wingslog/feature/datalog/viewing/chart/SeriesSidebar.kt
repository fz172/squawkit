package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.common.compose.StatusChip
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.StatusTier
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.datalog.DataLogSeries
import dev.fanfly.wingslog.feature.datalog.model.SeriesKey
import dev.fanfly.wingslog.feature.datalog.model.chart.isPlottable
import dev.fanfly.wingslog.feature.datalog.model.chart.isSelectable
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.datalog.sharedassets.generated.resources.Res
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_sidebar_hint
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_sidebar_info
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_sidebar_series
import wingslog.feature.datalog.sharedassets.generated.resources.data_log_tail_mismatch
import wingslog.feature.search.sharedassets.generated.resources.search_placeholder
import wingslog.feature.search.sharedassets.generated.resources.Res as SearchRes

enum class SidebarTab { SERIES, INFO }

/** One label and value line of the Info tab (PRD R26). */
data class InfoFact(val label: String, val value: String)

/**
 * Sidebar width on layouts with side navigation (design §11.7); no Spacing token covers it.
 *
 * 328 rather than a round 300 so the footer's fixed 320 dp ad unit (PRD R44a) fits inside the ad
 * card's own 4 dp insets. The unit does not adapt, so the column has to.
 */
val SidebarWidth: Dp = 328.dp

/**
 * The series sidebar (PRD R25, R26): a *Series* tab listing every plottable series with unit and
 * full-range min–max, tap adding to the target pane and a hold-drag adding to any pane; an *Info*
 * tab of record and source facts. The same content sits in the phone drawer.
 */
@Composable
fun SeriesSidebar(
  catalogue: List<DataLogSeries>,
  inTargetPane: Set<SeriesKey>,
  tab: SidebarTab,
  onTab: (SidebarTab) -> Unit,
  query: String,
  onQuery: (String) -> Unit,
  onAdd: (SeriesKey) -> Unit,
  dragState: SeriesDragState,
  onDrop: (SeriesDrag, DropTarget?) -> Unit,
  facts: List<InfoFact>,
  identityMismatch: Boolean,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxSize()) {
    TabRow(selectedTabIndex = tab.ordinal) {
      Tab(
        selected = tab == SidebarTab.SERIES,
        onClick = { onTab(SidebarTab.SERIES) },
        text = { Text(stringResource(Res.string.data_log_sidebar_series)) })
      Tab(
        selected = tab == SidebarTab.INFO,
        onClick = { onTab(SidebarTab.INFO) },
        text = { Text(stringResource(Res.string.data_log_sidebar_info)) })
    }
    when (tab) {
      SidebarTab.SERIES -> SeriesTab(
        catalogue,
        inTargetPane,
        query,
        onQuery,
        onAdd,
        dragState,
        onDrop
      )

      SidebarTab.INFO -> InfoTab(facts, identityMismatch)
    }
  }
}

@Composable
private fun SeriesTab(
  catalogue: List<DataLogSeries>,
  inTargetPane: Set<SeriesKey>,
  query: String,
  onQuery: (String) -> Unit,
  onAdd: (SeriesKey) -> Unit,
  dragState: SeriesDragState,
  onDrop: (SeriesDrag, DropTarget?) -> Unit,
) {
  val plottable = remember(catalogue, query) {
    val q = query.trim()
      .lowercase()
    catalogue.filter { it.isSelectable }
      .filter {
        q.isEmpty() || it.name.lowercase()
          .contains(q) || it.short_name.lowercase()
          .contains(q) || it.unit.lowercase()
          .contains(q)
      }
  }
  OutlinedTextField(
    value = query,
    onValueChange = onQuery,
    placeholder = { Text(stringResource(SearchRes.string.search_placeholder)) },
    singleLine = true,
    modifier = Modifier.fillMaxWidth()
      .padding(horizontal = Spacing.large, vertical = Spacing.small),
  )
  Text(
    stringResource(Res.string.data_log_sidebar_hint),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(
      horizontal = Spacing.large,
      vertical = Spacing.extraSmall
    ),
  )
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(plottable, key = { it.column }) { series ->
      SeriesRow(
        series,
        SeriesKey(series.column) in inTargetPane,
        onAdd,
        dragState,
        onDrop
      )
    }
  }
}

@Composable
private fun SeriesRow(
  series: DataLogSeries,
  inTarget: Boolean,
  onAdd: (SeriesKey) -> Unit,
  dragState: SeriesDragState,
  onDrop: (SeriesDrag, DropTarget?) -> Unit,
) {
  val key = SeriesKey(series.column)
  val label = series.short_name.ifBlank { series.name }
  var origin by remember { mutableStateOf(Offset.Zero) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .onGloballyPositioned { origin = it.positionInWindow() }
      .clickable { onAdd(key) }
      // A row is a drag source too: a dropped row lands on that pane rather than the target.
      .seriesDragSource(
        key,
        label,
        from = null,
        origin = { origin },
        dragState = dragState,
        onDrop = onDrop
      )
      .padding(horizontal = Spacing.large, vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    // Text selection would otherwise claim a mouse drag on the label before the row sees it.
    DisableSelection {
      Column(modifier = Modifier.weight(1f)) {
        Text(series.name, style = MaterialTheme.typography.bodyMedium)
        Text(
          // A position series has no range to state; its min and max are both zero.
          text = listOfNotNull(
            series.unit.takeIf { it.isNotBlank() },
            "${formatSeriesValue(series.min.toFloat())} – ${
              formatSeriesValue(
                series.max.toFloat()
              )
            }"
              .takeIf { series.isPlottable },
          ).joinToString(" · "),
          style = WingslogTypography.dataSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (inTarget) {
      Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(Spacing.large)
      )
    }
  }
}

@Composable
private fun InfoTab(facts: List<InfoFact>, identityMismatch: Boolean) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(
      vertical = Spacing.small
    )
  ) {
    if (identityMismatch) {
      item {
        StatusChip(
          label = stringResource(Res.string.data_log_tail_mismatch),
          tier = StatusTier.CAUTION,
          modifier = Modifier.padding(
            horizontal = Spacing.large,
            vertical = Spacing.small
          ),
        )
      }
    }
    items(facts, key = { it.label }) { fact ->
      Row(
        modifier = Modifier.fillMaxWidth()
          .padding(horizontal = Spacing.large, vertical = Spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          fact.label,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(fact.value, style = WingslogTypography.dataSmall)
      }
    }
  }
}
