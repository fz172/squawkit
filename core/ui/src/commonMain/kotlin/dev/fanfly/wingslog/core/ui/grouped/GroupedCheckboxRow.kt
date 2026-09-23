package dev.fanfly.wingslog.core.ui.grouped

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

@Composable
fun GroupedCheckboxRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  enabled: Boolean = true,
  titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
  leading: (@Composable RowScope.() -> Unit)? = null,
) {
  GroupedRow(
    title = title,
    subtitle = subtitle,
    titleStyle = titleStyle,
    enabled = enabled,
    onClick = { onCheckedChange(!checked) },
    leading = leading,
    trailing = {
      Checkbox(
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
      )
    },
    modifier = modifier,
  )
}
