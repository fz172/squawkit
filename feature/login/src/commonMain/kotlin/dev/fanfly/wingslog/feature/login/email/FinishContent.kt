package dev.fanfly.wingslog.feature.login.email

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.continue_action
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.email_finish_body
import wingslog.feature.login.generated.resources.email_finish_title
import wingslog.core.sharedassets.generated.resources.Res as CoreRes

@Composable
internal fun FinishContent(
  email: String,
  onEmailChange: (String) -> Unit,
  fieldError: String?,
  generalError: String?,
  isWorking: Boolean,
  onContinue: () -> Unit,
) {
  StepHeading(
    stringResource(Res.string.email_finish_title),
    stringResource(Res.string.email_finish_body)
  )
  Spacer(Modifier.height(Spacing.large))

  EmailField(
    value = email,
    onValueChange = onEmailChange,
    isError = fieldError != null,
    enabled = !isWorking,
    onImeAction = onContinue,
  )
  fieldError?.let { ErrorLine(it) }

  Spacer(Modifier.height(Spacing.large))

  PrimaryButton(
    label = stringResource(CoreRes.string.continue_action),
    enabled = !isWorking && email.isNotBlank(),
    loading = isWorking,
    onClick = onContinue,
  )

  generalError?.let { Spacer(Modifier.height(Spacing.medium)); ErrorLine(it) }
}
