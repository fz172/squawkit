package dev.fanfly.wingslog.feature.export.update.selection.result

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
internal fun ResultPrimaryButton(
  label: String,
  icon: ImageVector?,
  onClick: () -> Unit
) {
  Button(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth()
      .height(Spacing.buttonHeight),
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp)
      )
      Spacer(Modifier.width(Spacing.small))
    }
    Text(text = label, style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
internal fun ResultSecondaryButton(
  label: String,
  icon: ImageVector?,
  modifier: Modifier = Modifier,
  plain: Boolean = false,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  if (plain) {
    TextButton(
      onClick = onClick,
      enabled = enabled,
      modifier = modifier.fillMaxWidth()
        .height(48.dp),
    ) {
      Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  } else {
    OutlinedButton(
      onClick = onClick,
      enabled = enabled,
      modifier = modifier.fillMaxWidth()
        .height(48.dp),
      shape = RoundedCornerShape(Spacing.chipCornerRadius),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Spacing.small))
      }
      Text(text = label)
    }
  }
}
