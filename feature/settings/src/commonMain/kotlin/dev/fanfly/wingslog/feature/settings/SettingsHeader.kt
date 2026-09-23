package dev.fanfly.wingslog.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.only
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.settings
import wingslog.feature.settings.generated.resources.settings_subtitle
import wingslog.core.sharedassets.generated.resources.Res as CoreRes
import wingslog.feature.settings.generated.resources.Res as SettingsRes

/**
 * Page title + subtitle shown only in sidebar mode, where the shell cedes its top bar to the
 * Settings section. On compact tiers the shell's app bar supplies the title instead.
 */
@Composable
internal fun SettingsHeader() {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
    Text(
      text = stringResource(CoreRes.string.settings),
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = stringResource(SettingsRes.string.settings_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
