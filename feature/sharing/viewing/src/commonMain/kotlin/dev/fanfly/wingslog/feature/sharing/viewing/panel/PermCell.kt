package dev.fanfly.wingslog.feature.sharing.viewing.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.statusColors

@Composable
internal fun PermCell(granted: Boolean, modifier: Modifier = Modifier) {
  Box(modifier, contentAlignment = Alignment.Center) {
    Icon(
      if (granted) Icons.Filled.Check else Icons.Filled.Remove,
      contentDescription = null,
      tint = if (granted) {
        MaterialTheme.statusColors.positive.accent
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      modifier = Modifier.size(18.dp),
    )
  }
}
