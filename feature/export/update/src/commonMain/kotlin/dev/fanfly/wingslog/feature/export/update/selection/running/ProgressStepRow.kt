package dev.fanfly.wingslog.feature.export.update.selection.running

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.statusColors

@Composable
internal fun ProgressStepRow(
  label: String,
  active: Boolean,
  complete: Boolean,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier.size(18.dp),
      contentAlignment = Alignment.Center,
    ) {
      when {
        complete -> Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = null,
          tint = MaterialTheme.statusColors.positive.accent,
          modifier = Modifier.size(18.dp),
        )

        active -> CircularProgressIndicator(
          modifier = Modifier.size(14.dp),
          strokeWidth = 2.dp,
          color = MaterialTheme.colorScheme.primary,
        )

        else -> Icon(
          imageVector = Icons.Default.RadioButtonUnchecked,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.outlineVariant,
          modifier = Modifier.size(14.dp),
        )
      }
    }
    Text(
      text = label,
      style = if (active) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
      color = when {
        active -> MaterialTheme.colorScheme.onSurface
        complete -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
      },
      fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
    )
  }
}
