package dev.fanfly.wingslog.feature.settings.developeroptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.settings.generated.resources.Res
import wingslog.feature.settings.generated.resources.developer_options_consent_test_device_placeholder
import wingslog.feature.settings.generated.resources.developer_options_consent_test_device_subtitle
import wingslog.feature.settings.generated.resources.developer_options_consent_test_device_title
import wingslog.feature.settings.generated.resources.developer_options_force_ads_subtitle
import wingslog.feature.settings.generated.resources.developer_options_force_ads_title

/**
 * Shows display ads regardless of the account's tier, so placement can be exercised without a real
 * free account. Overrides the tier check only — a build with no ad support stays ad-free.
 */
@Composable
fun DisplayAdsDeveloperSettings(
  forceAds: Boolean,
  onToggleForceAds: (Boolean) -> Unit,
  adConsentTestDeviceHashedId: String?,
  onAdConsentTestDeviceHashedIdChange: (String) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.medium),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(Res.string.developer_options_force_ads_title),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = stringResource(Res.string.developer_options_force_ads_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = forceAds, onCheckedChange = onToggleForceAds)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = Spacing.medium),
  ) {
    Text(
      text = stringResource(Res.string.developer_options_consent_test_device_title),
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = FontWeight.Medium,
    )
    Text(
      text = stringResource(Res.string.developer_options_consent_test_device_subtitle),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
      value = adConsentTestDeviceHashedId.orEmpty(),
      onValueChange = onAdConsentTestDeviceHashedIdChange,
      placeholder = { Text(stringResource(Res.string.developer_options_consent_test_device_placeholder)) },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = Spacing.small),
    )
  }
}
