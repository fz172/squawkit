package dev.fanfly.wingslog.feature.export.update.selection.result

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing

/** One compact icon-over-label action in the success screen's bottom action bar. */
@Composable
internal fun SuccessBarAction(
  icon: ImageVector,
  label: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  val contentColor = if (enabled) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.outline
  }
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(Spacing.chipCornerRadius))
      .let { if (enabled) it.clickable(onClick = onClick) else it }
      .padding(vertical = Spacing.small, horizontal = Spacing.extraSmall),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = contentColor,
      modifier = Modifier.size(22.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
      textAlign = TextAlign.Center,
      maxLines = 2,
    )
  }
}
