package dev.fanfly.wingslog.feature.search.viewing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
fun ActiveFilterChip(
  label: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(Spacing.chipCornerRadius),
    color = MaterialTheme.colorScheme.primaryContainer,
    border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.primary),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(
        start = Spacing.small,
        end = Spacing.extraSmall,
        top = Spacing.extraSmall,
        bottom = Spacing.extraSmall,
      ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      IconButton(onClick = onDismiss, modifier = Modifier.size(Spacing.large)) {
        Icon(
          Icons.Default.Close,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(Spacing.small),
        )
      }
    }
  }
}
