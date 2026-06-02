package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.fanfly.wingslog.core.ui.common.compose.GroupedCard
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
 * Renders [rows] inside a single [SettingsCard], inserting a full-width hairline divider between
 * adjacent rows (and none after the last). Centralises the divider logic so callers only build the
 * list of rows they want to show.
 */
@Composable
fun SettingsRowGroup(
  rows: List<@Composable () -> Unit>,
  modifier: Modifier = Modifier,
) {
  GroupedRowGroup(rows = rows, modifier = modifier)
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
