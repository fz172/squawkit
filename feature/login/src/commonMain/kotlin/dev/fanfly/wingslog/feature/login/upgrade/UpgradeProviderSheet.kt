package dev.fanfly.wingslog.feature.login.upgrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.auth.AuthProvider
import dev.fanfly.wingslog.core.ui.popup.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.login.chrome.LoginCard
import dev.fanfly.wingslog.feature.login.chrome.LoginRow
import dev.fanfly.wingslog.feature.login.chrome.LoginRowDivider
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.apple_logo
import wingslog.feature.login.generated.resources.google_logo
import wingslog.feature.login.generated.resources.ic_apple
import wingslog.feature.login.generated.resources.ic_google_rd_na
import wingslog.feature.login.generated.resources.provider_apple
import wingslog.feature.login.generated.resources.provider_email
import wingslog.feature.login.generated.resources.provider_google
import wingslog.feature.login.generated.resources.upgrade_picker_body
import wingslog.feature.login.generated.resources.upgrade_picker_title

/**
 * The provider picker, as a bottom sheet. [providers] comes from `upgradeProvidersFor`, so this
 * renders whatever the platform offers without knowing which platform it is on.
 *
 * The buttons reuse `LoginCommon`'s colours, label style, icons and strings directly — living in
 * feature/login is what makes that possible, and it is why the sheet cannot drift away from the
 * full-screen login page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpgradeProviderSheet(
  providers: List<AuthProvider>,
  onSelect: (AuthProvider) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.large)
        .padding(bottom = Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
        text = stringResource(Res.string.upgrade_picker_title),
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(
        text = stringResource(Res.string.upgrade_picker_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(Modifier.height(Spacing.small))

      // The same card and rows the login page draws, so the two surfaces cannot drift. No heading:
      // the sheet's own title is right above it.
      LoginCard {
        providers.forEachIndexed { index, provider ->
          if (index > 0) LoginRowDivider()
          when (provider) {
            AuthProvider.Google -> LoginRow(
              label = stringResource(Res.string.provider_google),
              enabled = true,
              onClick = { onSelect(AuthProvider.Google) },
              icon = {
                // The multi-colour mark must not be tinted.
                Icon(
                  painter = painterResource(Res.drawable.ic_google_rd_na),
                  contentDescription = stringResource(Res.string.google_logo),
                  modifier = Modifier.size(16.dp),
                  tint = Color.Unspecified,
                )
              },
            )

            AuthProvider.Apple -> LoginRow(
              label = stringResource(Res.string.provider_apple),
              enabled = true,
              onClick = { onSelect(AuthProvider.Apple) },
              icon = {
                Icon(
                  painter = painterResource(Res.drawable.ic_apple),
                  contentDescription = stringResource(Res.string.apple_logo),
                  modifier = Modifier.size(18.dp),
                  tint = MaterialTheme.colorScheme.onSurface,
                )
              },
            )

            // Email opens an address form rather than signing in. It used to be styled quieter to
            // say so; the row's chevron says it now, the same as on the login page.
            AuthProvider.Email -> LoginRow(
              label = stringResource(Res.string.provider_email),
              enabled = true,
              onClick = { onSelect(AuthProvider.Email) },
              icon = {
                Icon(
                  imageVector = Icons.Filled.Email,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                  tint = MaterialTheme.colorScheme.primary,
                )
              },
            )
          }
        }
      }
    }
  }
}
