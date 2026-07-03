package dev.fanfly.wingslog.feature.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.auth.SendLinkResult
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.email_back
import wingslog.feature.login.generated.resources.email_entry_hint
import wingslog.feature.login.generated.resources.email_entry_title
import wingslog.feature.login.generated.resources.email_finish_body
import wingslog.feature.login.generated.resources.email_finish_cta
import wingslog.feature.login.generated.resources.email_finish_title
import wingslog.feature.login.generated.resources.email_invalid
import wingslog.feature.login.generated.resources.email_link_error
import wingslog.feature.login.generated.resources.email_link_sent_body
import wingslog.feature.login.generated.resources.email_link_sent_title
import wingslog.feature.login.generated.resources.email_resend
import wingslog.feature.login.generated.resources.email_resend_in
import wingslog.feature.login.generated.resources.email_send_link
import wingslog.feature.login.generated.resources.email_use_different
import kotlin.time.Duration.Companion.milliseconds

private const val ResendCooldownSeconds = 60

private enum class EmailStep {
  /** Entering the address to send a link to. */
  Enter,

  /** A link was just sent; waiting for the user to open it. */
  Sent,

  /** A link was opened (this or another device); completing leg 2, prompting for email if needed. */
  Finish,
}

/**
 * The shared, dedicated passwordless email-link page used verbatim on Android, iOS, and web. Wears
 * the same header as [LoginScreen]; on web it replaces the marketing landing once the user commits
 * to logging in. See docs/account/email_link_signin_design.html.
 */
@Composable
fun EmailSignInScreen(
  loginViewModel: LoginViewModel,
  onBack: () -> Unit,
  onLoginSuccess: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val pendingLink by EmailLinkDeepLinks.pendingLink.collectAsState()

  var step by remember { mutableStateOf(EmailStep.Enter) }
  var email by remember { mutableStateOf("") }
  var sentTo by remember { mutableStateOf("") }
  var fieldError by remember { mutableStateOf<String?>(null) }
  var generalError by remember { mutableStateOf<String?>(null) }
  var isWorking by remember { mutableStateOf(false) }
  var secondsLeft by remember { mutableStateOf(0) }

  val invalidEmailMessage = stringResource(Res.string.email_invalid)
  val linkErrorMessage = stringResource(Res.string.email_link_error)

  // 1 Hz resend cooldown: re-launches each time secondsLeft changes, ticking down to zero.
  LaunchedEffect(secondsLeft) {
    if (secondsLeft > 0) {
      delay(1_000.milliseconds)
      secondsLeft--
    }
  }

  // A link arrived (deep link / fresh page load). Try to complete; if no email is stashed on this
  // device, drop into the Finish step so the user can confirm the address.
  LaunchedEffect(pendingLink) {
    val link = pendingLink ?: return@LaunchedEffect
    if (!loginViewModel.isEmailSignInLink(link)) return@LaunchedEffect
    step = EmailStep.Finish
    generalError = null
    val stashed = loginViewModel.pendingEmail()
    if (stashed != null) {
      email = stashed
      isWorking = true
      val user = loginViewModel.completeEmailLink(link, stashed)
      isWorking = false
      if (user != null) {
        EmailLinkDeepLinks.consume()
        onLoginSuccess()
      } else {
        generalError = linkErrorMessage
      }
    }
  }

  fun sendLink() {
    scope.launch {
      isWorking = true
      fieldError = null
      generalError = null
      try {
        when (val result = loginViewModel.sendEmailLink(email)) {
          is SendLinkResult.Sent -> {
            sentTo = result.email
            secondsLeft = ResendCooldownSeconds
            step = EmailStep.Sent
          }

          SendLinkResult.InvalidEmail -> fieldError = invalidEmailMessage
          is SendLinkResult.Failed -> generalError = result.message
        }
      } finally {
        isWorking = false
      }
    }
  }

  fun finishWithEnteredEmail() {
    val link = pendingLink ?: return
    scope.launch {
      isWorking = true
      fieldError = null
      generalError = null
      try {
        val user = loginViewModel.completeEmailLink(link, email)
        if (user != null) {
          EmailLinkDeepLinks.consume()
          onLoginSuccess()
        } else {
          generalError = linkErrorMessage
        }
      } finally {
        isWorking = false
      }
    }
  }

  LoginBackdrop {
    Spacer(Modifier.height(76.dp))

    LoginPlaneArt()

    Spacer(Modifier.weight(1f))

    LoginWordmark()

    Spacer(Modifier.height(Spacing.huge))

    when (step) {
      EmailStep.Enter -> EnterEmailContent(
        email = email,
        onEmailChange = { email = it; fieldError = null },
        fieldError = fieldError,
        generalError = generalError,
        isWorking = isWorking,
        onSend = ::sendLink,
        onBack = onBack,
      )

      EmailStep.Sent -> LinkSentContent(
        sentTo = sentTo,
        secondsLeft = secondsLeft,
        isWorking = isWorking,
        generalError = generalError,
        onResend = ::sendLink,
        onUseDifferent = {
          step = EmailStep.Enter
          generalError = null
        },
      )

      EmailStep.Finish -> FinishContent(
        email = email,
        onEmailChange = { email = it; fieldError = null },
        fieldError = fieldError,
        generalError = generalError,
        isWorking = isWorking,
        onContinue = ::finishWithEnteredEmail,
      )
    }

    Spacer(Modifier.height(Spacing.large))

    LoginLegalFooter()

    Spacer(Modifier.height(Spacing.large))
  }
}

@Composable
private fun EnterEmailContent(
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
      stringResource(Res.string.email_back),
      color = LoginOnBackgroundMuted,
      style = LoginSecondaryLabelStyle
    )
  }
}

@Composable
private fun LinkSentContent(
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
      color = LoginOnBackgroundMuted,
      style = LoginSecondaryLabelStyle
    )
  }
}

@Composable
private fun FinishContent(
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
    label = stringResource(Res.string.email_finish_cta),
    enabled = !isWorking && email.isNotBlank(),
    loading = isWorking,
    onClick = onContinue,
  )

  generalError?.let { Spacer(Modifier.height(Spacing.medium)); ErrorLine(it) }
}

@Composable
private fun StepHeading(title: String, subtitle: String? = null) {
  Text(
    text = title,
    style = TextStyle(
      fontWeight = FontWeight.SemiBold,
      fontSize = 20.sp,
      color = LoginOnBackground
    ),
  )
  if (subtitle != null) {
    Spacer(Modifier.height(Spacing.small))
    Text(
      text = subtitle,
      style = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = LoginOnBackgroundMuted
      ),
    )
  }
}

@Composable
private fun EmailField(
  value: String,
  onValueChange: (String) -> Unit,
  isError: Boolean,
  enabled: Boolean,
  onImeAction: () -> Unit,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = Modifier.fillMaxWidth(),
    enabled = enabled,
    singleLine = true,
    isError = isError,
    placeholder = {
      Text(
        stringResource(Res.string.email_entry_hint),
        color = LoginOnBackgroundMuted.copy(alpha = 0.6f)
      )
    },
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.Email,
      imeAction = ImeAction.Go
    ),
    keyboardActions = KeyboardActions(onGo = { onImeAction() }),
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = LoginOnBackground,
      unfocusedTextColor = LoginOnBackground,
      cursorColor = LoginOnBackground,
      focusedBorderColor = LoginOnBackground.copy(alpha = 0.7f),
      unfocusedBorderColor = LoginOnBackgroundMuted.copy(alpha = 0.4f),
    ),
  )
}

@Composable
private fun PrimaryButton(
  label: String,
  enabled: Boolean,
  loading: Boolean,
  onClick: () -> Unit,
) {
  Button(
    modifier = Modifier
      .fillMaxWidth()
      .height(54.dp),
    enabled = enabled,
    shape = RoundedCornerShape(Spacing.buttonCornerRadius),
    colors = ButtonDefaults.buttonColors(
      containerColor = LoginOnBackground,
      contentColor = LoginBackground,
      disabledContainerColor = LoginOnBackground.copy(alpha = 0.4f),
      disabledContentColor = LoginBackground.copy(alpha = 0.4f),
    ),
    onClick = onClick,
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(Spacing.xLarge),
        strokeWidth = 2.dp,
        color = LoginBackground,
      )
    } else {
      Text(text = label, style = LoginButtonLabelStyle)
    }
  }
}

@Composable
private fun ErrorLine(message: String) {
  Box(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = message,
      color = LoginErrorText,
      style = LoginErrorStyle,
      textAlign = TextAlign.Start,
      modifier = Modifier.align(Alignment.CenterStart),
    )
  }
}

private fun formatCountdown(totalSeconds: Int): String {
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  val secondsPadded = if (seconds < 10) "0$seconds" else "$seconds"
  return "$minutes:$secondsPadded"
}
