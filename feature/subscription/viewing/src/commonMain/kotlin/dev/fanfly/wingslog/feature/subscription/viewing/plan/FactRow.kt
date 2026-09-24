package dev.fanfly.wingslog.feature.subscription.viewing.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun FactRow(
  label: String,
  value: @Composable RowScope.() -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = Spacing.small),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically, content = value)
  }
}

@Composable
internal fun FactIcon(icon: ImageVector) {
  Icon(
    imageVector = icon,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.size(Spacing.large),
  )
  Spacer(Modifier.width(Spacing.small))
}
