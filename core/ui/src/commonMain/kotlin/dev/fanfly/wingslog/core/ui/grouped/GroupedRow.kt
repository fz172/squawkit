package dev.fanfly.wingslog.core.ui.grouped

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.theme.Spacing

@Composable
fun GroupedRow(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
  titleColor: Color = MaterialTheme.colorScheme.onSurface,
  subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  enabled: Boolean = true,
  onClick: (() -> Unit)? = null,
  leading: (@Composable RowScope.() -> Unit)? = null,
  trailing: (@Composable RowScope.() -> Unit)? = null,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (onClick != null) Modifier.clickable(
          enabled = enabled,
          onClick = onClick
        )
        else Modifier
      )
      .padding(horizontal = Spacing.xLarge, vertical = Spacing.large),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (leading != null) {
      leading()
      Spacer(modifier = Modifier.width(Spacing.large))
    }

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
        text = title,
        style = titleStyle,
        fontWeight = FontWeight.SemiBold,
        color = titleColor,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = subtitleColor,
        )
      }
    }

    if (trailing != null) {
      Spacer(modifier = Modifier.width(Spacing.large))
      trailing()
    }
  }
}
