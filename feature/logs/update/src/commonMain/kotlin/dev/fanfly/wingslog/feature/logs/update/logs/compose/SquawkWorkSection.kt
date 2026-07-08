package dev.fanfly.wingslog.feature.logs.update.logs.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.remove
import wingslog.feature.squawk.sharedassets.generated.resources.no_squawk_work_recorded
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.squawk.sharedassets.generated.resources.Res as SquawkRes

@Composable
fun SquawkWorkSection(
  selectedIds: List<String>,
  availableSquawks: List<Squawk>,
  onRemove: (squawkId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(Spacing.small)
  ) {
    if (selectedIds.isEmpty()) {
      Text(
        text = stringResource(SquawkRes.string.no_squawk_work_recorded),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      selectedIds.forEach { squawkId ->
        val squawk = availableSquawks.firstOrNull { it.id == squawkId }
        val title = squawk?.title ?: squawkId
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = { onRemove(squawkId) }) {
            Icon(
              Icons.Default.Close,
              contentDescription = stringResource(CoreRes.string.remove),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
