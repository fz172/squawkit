package dev.fanfly.wingslog.feature.login.upgrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.auth.AuthProvider
import dev.fanfly.wingslog.core.ui.popup.ModalBottomSheet
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.login.chrome.LoginButtonHeight
import dev.fanfly.wingslog.feature.login.chrome.LoginButtonLabelStyle
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.cancel
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.upgrade_merge_body
import wingslog.feature.login.generated.resources.upgrade_merge_confirm
import wingslog.feature.login.generated.resources.upgrade_merge_reauth
import wingslog.feature.login.generated.resources.upgrade_merge_title
import wingslog.feature.login.generated.resources.upgrade_provider_apple
import wingslog.feature.login.generated.resources.upgrade_provider_email
import wingslog.feature.login.generated.resources.upgrade_provider_google
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

/**
 * The interstitial for a collision: this provider account already has SquawkIt records.
 *
 * A bottom sheet rather than a dialog so it reads as a continuation of the picker the user just
 * used. Its real job on iOS is to give the second Apple sheet a reason — without it, that prompt
 * looks like the first one silently failed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpgradeMergeSheet(
  provider: AuthProvider,
  needsReauthorization: Boolean,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val name = stringResource(
    when (provider) {
      AuthProvider.Apple -> Res.string.upgrade_provider_apple
      AuthProvider.Google -> Res.string.upgrade_provider_google
      AuthProvider.Email -> Res.string.upgrade_provider_email
    }
  )

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.large)
        .padding(bottom = Spacing.extraLarge),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
        text = stringResource(Res.string.upgrade_merge_title, name),
        style = MaterialTheme.typography.headlineSmall,
      )
      Text(
        text = stringResource(Res.string.upgrade_merge_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (needsReauthorization) {
        Text(
          text = stringResource(Res.string.upgrade_merge_reauth, name),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(Spacing.small))

      Button(
        modifier = Modifier
          .fillMaxWidth()
          .height(LoginButtonHeight),
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        onClick = onConfirm,
      ) {
        Text(
          text = stringResource(Res.string.upgrade_merge_confirm),
          style = LoginButtonLabelStyle,
        )
      }
      OutlinedButton(
        modifier = Modifier
          .fillMaxWidth()
          .height(LoginButtonHeight),
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        onClick = onDismiss,
      ) {
        Text(
          text = stringResource(CoreRes.string.cancel),
          style = LoginButtonLabelStyle,
        )
      }
    }
  }
}
