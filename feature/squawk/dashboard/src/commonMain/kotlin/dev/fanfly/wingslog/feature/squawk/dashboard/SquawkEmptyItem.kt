package dev.fanfly.wingslog.feature.squawk.dashboard

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.template.LocalThingLexicon
import dev.fanfly.wingslog.core.template.squawkEmptyHint
import dev.fanfly.wingslog.core.template.squawkNoun
import dev.fanfly.wingslog.core.ui.list.EmptyState
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.search.viewing.NoRecordsMatch
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.squawk.sharedassets.generated.resources.Res
import wingslog.feature.squawk.sharedassets.generated.resources.no_closed_squawks
import wingslog.feature.squawk.sharedassets.generated.resources.no_open_squawks

/** What the list shows in place of rows: no match, nothing open, or nothing closed. */
@Composable
internal fun SquawkEmptyItem(
  filterActive: Boolean,
  showClosed: Boolean,
  onClearFilters: () -> Unit,
) {
  val squawkNoun = LocalThingLexicon.current.squawkNoun
  if (filterActive) {
    NoRecordsMatch(
      nounPlural = squawkNoun.plural,
      onClearFilters = onClearFilters,
      modifier = Modifier.padding(top = Spacing.medium),
    )
  } else if (!showClosed) {
    EmptyState(
      title = stringResource(
        Res.string.no_open_squawks,
        squawkNoun.plural
      ),
      description = LocalThingLexicon.current.squawkEmptyHint,
      icon = Icons.Default.CheckCircle,
    )
  } else {
    Text(
      text = stringResource(
        Res.string.no_closed_squawks,
        squawkNoun.plural
      ),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(vertical = Spacing.large),
    )
  }
}
