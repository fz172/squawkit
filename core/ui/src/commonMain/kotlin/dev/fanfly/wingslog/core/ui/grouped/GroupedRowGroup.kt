package dev.fanfly.wingslog.core.ui.grouped

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme

/**
 * Renders rows inside one [GroupedCard], inserting dividers between adjacent rows.
 *
 * @param dividerStartInset where each divider starts. Defaults to [GroupedDividerInset] for rows
 *   that lead with an icon chip; pass [Spacing.xLarge] for rows that do not.
 */
@Composable
fun GroupedRowGroup(
  rows: List<@Composable () -> Unit>,
  modifier: Modifier = Modifier,
  dividerStartInset: Dp = GroupedDividerInset,
) {
  GroupedCard(modifier = modifier) {
    rows.forEachIndexed { index, row ->
      row()
      if (index < rows.lastIndex) {
        HorizontalDivider(
          thickness = Spacing.hairline,
          color = MaterialTheme.colorScheme.outlineVariant,
          modifier = Modifier.padding(start = dividerStartInset),
        )
      }
    }
  }
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
