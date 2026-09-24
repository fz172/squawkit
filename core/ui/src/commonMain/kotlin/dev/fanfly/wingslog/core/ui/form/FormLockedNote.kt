package dev.fanfly.wingslog.core.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** Why a value cannot be changed — so a locked field reads as intent, not as a tap that missed. */
@Composable
fun FormLockedNote(text: String, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Icon(
      imageVector = Icons.Outlined.Lock,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(Spacing.large),
    )
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
