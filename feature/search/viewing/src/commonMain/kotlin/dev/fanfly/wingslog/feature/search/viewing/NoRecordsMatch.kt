package dev.fanfly.wingslog.feature.search.viewing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.Res
import wingslog.feature.search.sharedassets.generated.resources.clear_filters
import wingslog.feature.search.sharedassets.generated.resources.no_records_match

@Composable
fun NoRecordsMatch(
  nounPlural: String,
  onClearFilters: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Text(
      text = stringResource(Res.string.no_records_match, nounPlural),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onClearFilters) {
      Text(stringResource(Res.string.clear_filters))
    }
  }
}
