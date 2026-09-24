package dev.fanfly.wingslog.feature.login.email

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.login.chrome.LoginSecondaryLabelStyle
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.back
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.email_entry_title
import wingslog.feature.login.generated.resources.email_send_link
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun EnterEmailContent(
  email: String,
  onEmailChange: (String) -> Unit,
  fieldError: String?,
  generalError: String?,
  isWorking: Boolean,
  onSend: () -> Unit,
  onBack: () -> Unit,
) {
  StepHeading(stringResource(Res.string.email_entry_title))
  Spacer(Modifier.height(Spacing.large))

  EmailField(
    value = email,
    onValueChange = onEmailChange,
    isError = fieldError != null,
    enabled = !isWorking,
    onImeAction = onSend,
  )
  fieldError?.let { ErrorLine(it) }

  Spacer(Modifier.height(Spacing.large))

  PrimaryButton(
    label = stringResource(Res.string.email_send_link),
    enabled = !isWorking && email.isNotBlank(),
    loading = isWorking,
    onClick = onSend,
  )

  generalError?.let { Spacer(Modifier.height(Spacing.medium)); ErrorLine(it) }

  Spacer(Modifier.height(Spacing.small))
  TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
    Text(
      stringResource(CoreRes.string.back),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = LoginSecondaryLabelStyle
    )
  }
}
