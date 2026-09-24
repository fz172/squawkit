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
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.email_link_sent_body
import wingslog.feature.login.generated.resources.email_link_sent_title
import wingslog.feature.login.generated.resources.email_resend
import wingslog.feature.login.generated.resources.email_resend_in
import wingslog.feature.login.generated.resources.email_use_different

@Composable
internal fun LinkSentContent(
  sentTo: String,
  secondsLeft: Int,
  isWorking: Boolean,
  generalError: String?,
  onResend: () -> Unit,
  onUseDifferent: () -> Unit,
) {
  StepHeading(
    stringResource(Res.string.email_link_sent_title),
    stringResource(Res.string.email_link_sent_body, sentTo),
  )

  Spacer(Modifier.height(Spacing.large))

  val resendLabel = if (secondsLeft > 0) {
    stringResource(Res.string.email_resend_in, formatCountdown(secondsLeft))
  } else {
    stringResource(Res.string.email_resend)
  }
  PrimaryButton(
    label = resendLabel,
    enabled = secondsLeft == 0 && !isWorking,
    loading = isWorking,
    onClick = onResend,
  )

  generalError?.let { Spacer(Modifier.height(Spacing.medium)); ErrorLine(it) }

  Spacer(Modifier.height(Spacing.small))
  TextButton(onClick = onUseDifferent, modifier = Modifier.fillMaxWidth()) {
    Text(
      stringResource(Res.string.email_use_different),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      style = LoginSecondaryLabelStyle
    )
  }
}

private fun formatCountdown(totalSeconds: Int): String {
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  val secondsPadded = if (seconds < 10) "0$seconds" else "$seconds"
  return "$minutes:$secondsPadded"
}
