package dev.fanfly.wingslog.feature.settings.section

import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.runtime.Composable
import dev.fanfly.wingslog.core.ui.grouped.GroupedSection
import dev.fanfly.wingslog.feature.settings.SettingsUiState
import dev.fanfly.wingslog.feature.settings.row.SettingsRow
import dev.fanfly.wingslog.feature.settings.row.SettingsRowGroup
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.settings.generated.resources.developer_options
import wingslog.feature.settings.generated.resources.settings_about
import wingslog.feature.settings.generated.resources.settings_about_subtitle
import wingslog.feature.settings.generated.resources.settings_ad_privacy
import wingslog.feature.settings.generated.resources.settings_ad_privacy_subtitle
import wingslog.feature.settings.generated.resources.settings_developer_options_subtitle
import wingslog.feature.settings.generated.resources.settings_section_support
import wingslog.feature.settings.generated.resources.Res as SettingsRes

/** Diagnostics logging, ad privacy, About, and Developer Options on the builds that have it. */
@Composable
internal fun SupportSection(
  user: SettingsUiState,
  firebaseLoggingEnabled: Boolean,
  onFirebaseLoggingChange: (Boolean) -> Unit,
  onPresentAdPrivacyOptions: () -> Unit,
  onOpenAbout: () -> Unit,
  onOpenDeveloperOptions: () -> Unit,
) {
  GroupedSection(stringResource(SettingsRes.string.settings_section_support)) {
    SettingsRowGroup(
      buildList {
        add {
          FirebaseLoggingSettingRow(
            enabled = firebaseLoggingEnabled,
            onEnabledChange = onFirebaseLoggingChange,
          )
        }
        // Only when there's actually a CMP form to re-present right now — not just wherever
        // this build ships ads — so tapping the row never silently does nothing (#384).
        if (user.isAdPrivacyOptionsAvailable) {
          add {
            SettingsRow(
              icon = Icons.Default.PrivacyTip,
              title = stringResource(SettingsRes.string.settings_ad_privacy),
              subtitle = stringResource(SettingsRes.string.settings_ad_privacy_subtitle),
              onClick = onPresentAdPrivacyOptions,
            )
          }
        }
        add {
          SettingsRow(
            icon = Icons.Default.Info,
            title = stringResource(SettingsRes.string.settings_about),
            subtitle = stringResource(SettingsRes.string.settings_about_subtitle),
            onClick = onOpenAbout,
          )
        }
        // Developer Options is a developer surface: only on debug and dogfood-style builds,
        // never in release.
        if (user.isDeveloperOptionsSupported) {
          add {
            SettingsRow(
              icon = Icons.Default.Code,
              title = stringResource(SettingsRes.string.developer_options),
              subtitle = stringResource(SettingsRes.string.settings_developer_options_subtitle),
              onClick = onOpenDeveloperOptions,
            )
          }
        }
      }
    )
  }
}
