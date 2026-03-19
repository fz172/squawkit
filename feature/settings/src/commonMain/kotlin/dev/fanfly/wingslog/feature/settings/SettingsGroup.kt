package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.ui.theme.WingslogTheme
import dev.fanfly.wingslog.feature.settings.data.SettingItem
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Displays a group of settings items in a card.
 */
@Composable
private fun SettingsGroup(items: List<SettingItem>) {
  Card(
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column {
      items.forEachIndexed { index, item ->
        SettingsRow(
          icon = item.icon,
          title = item.title,
          onClick = item.onClick,
          settingsLevel = item.settingsLevel
        )
        if (index < items.lastIndex) {
          HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
      }
    }
  }
}

@Preview
@Composable
fun SettingsGroupPreview() {
  val items: List<SettingItem> = listOf(
    SettingItem(
      icon = Icons.AutoMirrored.Filled.CallMerge,
      title = "Test 1",
    ),
    SettingItem(
      icon = Icons.AutoMirrored.Filled.CallMerge,
      title = "Test 2",
      settingsLevel = SettingsLevel.DANGER
    )
  )
  WingslogTheme {
    SettingsGroup(
      items = items,
    )
  }
}