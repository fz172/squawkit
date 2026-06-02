package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.fanfly.wingslog.core.ui.theme.Spacing
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
  Card(
    shape = RoundedCornerShape(Spacing.large),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = Spacing.none),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant
    ),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(content = content)
  }
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
  SettingsCard(modifier = modifier) {
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
