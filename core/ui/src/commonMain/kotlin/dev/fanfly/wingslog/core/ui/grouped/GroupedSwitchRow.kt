package dev.fanfly.wingslog.core.ui.grouped

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A [GroupedRow] whose trailing control is a [Switch]; the whole row toggles it. */
@Composable
fun GroupedSwitchRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  enabled: Boolean = true,
  leading: (@Composable RowScope.() -> Unit)? = null,
) {
  val contentAlpha = if (enabled) 1f else 0.42f
  GroupedRow(
    title = title,
    subtitle = subtitle,
    titleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
    subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
    enabled = enabled,
    onClick = { onCheckedChange(!checked) },
    leading = leading,
    trailing = {
      Switch(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
      )
    },
    modifier = modifier,
  )
}
