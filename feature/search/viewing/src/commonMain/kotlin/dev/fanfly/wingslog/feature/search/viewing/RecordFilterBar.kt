package dev.fanfly.wingslog.feature.search.viewing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.search.model.RecordFilter
import dev.fanfly.wingslog.feature.search.model.TimeWindow
import dev.fanfly.wingslog.thing.ComponentType
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.Res
import wingslog.feature.search.sharedassets.generated.resources.clear
import wingslog.feature.search.sharedassets.generated.resources.clear_search
import wingslog.feature.search.sharedassets.generated.resources.count_of_total
import wingslog.feature.search.sharedassets.generated.resources.filters

/** Search field, filter button and the chips for what is applied. Stateless; one per tab. */
@Composable
fun RecordFilterBar(
  filter: RecordFilter,
  placeholder: String,
  showComponentFilter: Boolean,
  componentLabel: @Composable (ComponentType) -> String,
  onQueryChange: (String) -> Unit,
  onOpenFilters: () -> Unit,
  onRemoveComponent: (ComponentType) -> Unit,
  onClearTime: () -> Unit,
  modifier: Modifier = Modifier,
  dueWithin: Boolean = false,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = Spacing.screenPadding, end = Spacing.small, top = Spacing.small, bottom = Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      OutlinedTextField(
        value = filter.query,
        onValueChange = onQueryChange,
        modifier = Modifier.weight(1f),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
          if (filter.query.isNotBlank()) {
            IconButton(onClick = { onQueryChange("") }) {
              Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.clear_search))
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(Spacing.smallCornerRadius),
        colors = OutlinedTextFieldDefaults.colors(
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
      )
      FilterButton(active = filter.hasNonQueryFilter, onClick = onOpenFilters)
    }

    val componentChips = if (showComponentFilter) filter.components.toList() else emptyList()
    val time = filter.time
    if (componentChips.isNotEmpty() || time != TimeWindow.All) {
      LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.small),
      ) {
        items(componentChips) { component ->
          ActiveFilterChip(label = componentLabel(component), onDismiss = { onRemoveComponent(component) })
        }
        if (time != TimeWindow.All) {
          item { ActiveFilterChip(label = time.chipLabel(dueWithin), onDismiss = onClearTime) }
        }
      }
    }
  }
}

@Composable
private fun FilterButton(active: Boolean, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(Spacing.smallCornerRadius),
    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(
      Spacing.hairline,
      if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    ),
    modifier = Modifier.size(Spacing.buttonHeight),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        Icons.Default.FilterList,
        contentDescription = stringResource(Res.string.filters),
        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (active) {
        Badge(
          containerColor = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.small),
        )
      }
    }
  }
}

/** “5 of 12 work logs”, with a Clear action once anything narrows the list. */
@Composable
fun RecordCountRow(
  shown: Int,
  total: Int,
  nounPlural: String,
  filterActive: Boolean,
  onClear: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = Spacing.screenPadding, vertical = Spacing.extraSmall),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(Res.string.count_of_total, shown, total, nounPlural).uppercase(),
      style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.6.sp),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    if (filterActive) {
      Text(
        text = stringResource(Res.string.clear),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.small).clickableText(onClear),
      )
    }
  }
}
