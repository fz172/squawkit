package dev.fanfly.wingslog.dev.fanfly.wingslog.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.dev.fanfly.wingslog.settings.SettingsLevel.DEFAULT
import dev.fanfly.wingslog.ui.theme.WingslogTheme

enum class SettingsLevel {
  DEFAULT,
  DANGER
}

/**
 * A single clickable row for a setting item.
 */
@Composable
fun SettingsRow(
  icon: ImageVector,
  title: String,
  onClick: () -> Unit,
  settingsLevel: SettingsLevel = DEFAULT,
) {
  val tint =
    if (settingsLevel == DEFAULT) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // --- Icon ---
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        modifier = Modifier.size(24.dp),
        tint = tint
      )
    }

    Spacer(modifier = Modifier.width(16.dp))

    // --- Title ---
    Text(
      text = title,
      fontSize = 16.sp,
      modifier = Modifier.weight(1f),
      color = tint,
    )
    if (settingsLevel != SettingsLevel.DANGER) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = tint
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun SettingsRowPreview() {
  WingslogTheme {
    SettingsRow(
      icon = Icons.AutoMirrored.Filled.ArrowBack,
      title = "Test",
      onClick = {},
    )
  }
}
