package dev.fanfly.wingslog.feature.search.viewing.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.search.sharedassets.generated.resources.Res
import wingslog.feature.search.sharedassets.generated.resources.filter_pick_any
import wingslog.feature.search.sharedassets.generated.resources.filter_pick_one

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSection(
  /** A question, not a field name: "How urgent", not "PRIORITY". */
  label: String,
  pickOne: Boolean,
  note: String? = null,
  chips: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Row(
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      verticalAlignment = Alignment.Bottom,
    ) {
      Text(label, style = MaterialTheme.typography.titleSmall)
      Text(
        stringResource(if (pickOne) Res.string.filter_pick_one else Res.string.filter_pick_any),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) { chips() }
    if (note != null) {
      Text(
        note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
