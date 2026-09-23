package dev.fanfly.wingslog.feature.export.update.selection.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors

@Composable
internal fun DeliveryFailureSection(failure: DeliveryFailure) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Icon(
      imageVector = Icons.Default.ErrorOutline,
      contentDescription = null,
      tint = MaterialTheme.statusColors.critical.accent,
      modifier = Modifier.size(20.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
      Text(
        text = failure.title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.statusColors.critical.accent,
      )
      Text(
        text = failure.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
