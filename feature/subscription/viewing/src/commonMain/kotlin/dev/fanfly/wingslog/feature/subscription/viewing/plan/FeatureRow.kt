package dev.fanfly.wingslog.feature.subscription.viewing.plan

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.core.ui.grouped.GroupedRow
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.core.ui.theme.statusColors

@Composable
internal fun FeatureRow(
  icon: ImageVector,
  label: String,
  included: Boolean,
  value: String? = null,
) {
  GroupedRow(
    title = label,
    titleStyle = MaterialTheme.typography.bodyLarge,
    leading = {
      Icon(
        imageVector = if (included) Icons.Default.Check else icon,
        contentDescription = null,
        modifier = Modifier.size(Spacing.xLarge + Spacing.extraSmall / 2),
        tint = if (included) MaterialTheme.statusColors.positive.accent
        else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    trailing = value?.let {
      {
        Text(
          text = it,
          style = WingslogTypography.dataMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
  )
}
