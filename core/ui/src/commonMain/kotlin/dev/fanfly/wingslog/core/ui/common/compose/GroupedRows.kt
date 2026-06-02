package dev.fanfly.wingslog.core.ui.common.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme

private val IconChipSize = 48.dp
private val IconChipRadius = 13.dp
private val IconSize = 22.dp

/**
 * Bordered grouped surface for vertically stacked rows.
 */
@Composable
fun GroupedCard(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    shape = RoundedCornerShape(Spacing.large),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant,
    ),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(content = content)
  }
}

/**
 * Renders rows inside one [GroupedCard], inserting dividers between adjacent rows.
 */
@Composable
fun GroupedRowGroup(
  rows: List<@Composable () -> Unit>,
  modifier: Modifier = Modifier,
) {
  GroupedCard(modifier = modifier) {
    rows.forEachIndexed { index, row ->
      row()
      if (index < rows.lastIndex) {
        HorizontalDivider(
          thickness = Spacing.hairline,
          color = MaterialTheme.colorScheme.outlineVariant,
        )
      }
    }
  }
}

@Composable
fun GroupedLeadingIconChip(
  icon: ImageVector,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
  iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
  Box(
    modifier = modifier
      .size(IconChipSize)
      .clip(RoundedCornerShape(IconChipRadius))
      .background(containerColor),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      modifier = Modifier.size(IconSize),
      tint = iconTint,
    )
  }
}

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
      .padding(Spacing.xLarge),
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

@Preview
@Composable
private fun GroupedRowGroupPreview() {
  WingslogTheme {
    Box(
      modifier = Modifier
        .background(MaterialTheme.colorScheme.background)
        .padding(Spacing.medium)
    ) {
      GroupedRowGroup(
        rows = listOf(
          {
            GroupedRow(
              title = "Maintenance Task",
              subtitle = "Due in 50 hours",
              leading = {
                GroupedLeadingIconChip(
                  icon = Icons.Default.Build,
                  contentDescription = null
                )
              }
            )
          },
          {
            GroupedRow(
              title = "Inspection",
              subtitle = "Scheduled for next week",
              leading = {
                GroupedLeadingIconChip(
                  icon = Icons.Default.Schedule,
                  contentDescription = null
                )
              }
            )
          },
          {
            GroupedCheckboxRow(
              title = "Checkbox Item",
              checked = true,
              onCheckedChange = {},
              leading = {
                GroupedLeadingIconChip(
                  icon = Icons.Default.Info,
                  contentDescription = null
                )
              }
            )
          }
        )
      )
    }
  }
}
