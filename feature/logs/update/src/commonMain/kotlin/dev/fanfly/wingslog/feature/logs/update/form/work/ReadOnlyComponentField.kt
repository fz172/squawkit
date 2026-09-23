package dev.fanfly.wingslog.feature.logs.update.form.work

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography

/**
 * Read-only labelled value box used when a component has no choice to make (the airframe, or an
 * thing with a single engine). Styled like a disabled outlined field so it reads as informational
 * rather than interactive.
 */
@Composable
internal fun ReadOnlyComponentField(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .height(64.dp)
      .border(
        width = Spacing.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(Spacing.chipCornerRadius)
      )
      .background(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(Spacing.chipCornerRadius)
      )
      .padding(horizontal = Spacing.large),
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = WingslogTypography.dataMedium,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}
