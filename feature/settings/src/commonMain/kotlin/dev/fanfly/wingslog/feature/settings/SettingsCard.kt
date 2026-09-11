package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import dev.fanfly.wingslog.core.ui.common.compose.GroupedCard
import dev.fanfly.wingslog.core.ui.common.compose.GroupedDividerInset
import dev.fanfly.wingslog.core.ui.common.compose.GroupedRowGroup
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme

/**
 * A grouped settings surface: a bordered, 16dp-radius card that stacks its rows top to bottom.
 * Matches the `.card` group in the Settings design handoff — flat (no elevation), a hairline
 * `outlineVariant` border, and the standard card container tone.
 */
@Composable
fun SettingsCard(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  GroupedCard(modifier = modifier, content = content)
}

/**
 * Renders [rows] inside a single [SettingsCard], inserting a hairline divider between adjacent
 * rows (and none after the last). Centralises the divider logic so callers only build the list of
 * rows they want to show. Dividers inset to the text column of a chip-led row by default.
 */
@Composable
fun SettingsRowGroup(
  rows: List<@Composable () -> Unit>,
  modifier: Modifier = Modifier,
  dividerStartInset: Dp = GroupedDividerInset,
) {
  GroupedRowGroup(rows = rows, modifier = modifier, dividerStartInset = dividerStartInset)
}

@Preview
@Composable
private fun SettingsRowGroupPreview() {
  WingslogTheme {
    SettingsRowGroup(
      rows = listOf(
        {
          SettingsRow(
            icon = Icons.Default.Engineering,
            title = "Technician Profiles",
            subtitle = "Manage technician info who performs maintenance work",
            onClick = {},
          )
        },
        {
          SettingsRow(
            icon = Icons.Default.CloudSync,
            title = "Backup & Sync",
            subtitle = "Keep your records synced across devices",
            onClick = {},
          )
        },
      ),
    )
  }
}
