package dev.fanfly.wingslog.feature.login.email

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fanfly.wingslog.core.auth.EmailLinkDeepLinks
import dev.fanfly.wingslog.core.auth.SendLinkResult
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.login.LoginScreen
import dev.fanfly.wingslog.feature.login.chrome.LoginLegalFooter
import dev.fanfly.wingslog.feature.login.chrome.LoginMark
import dev.fanfly.wingslog.feature.login.chrome.LoginScaffold
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.back
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.email_invalid
import wingslog.feature.login.generated.resources.email_link_error

private const val ResendCooldownSeconds = 60

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

  // No top bar and a resting mark: this screen is reached *from* the login card, so the way back is
  // the step's own "All log-in options" button, and replaying the hero sequence would be noise.
  LoginScaffold {
    LoginMark(animate = false)

    Spacer(Modifier.height(Spacing.extraLarge))

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

    Spacer(Modifier.height(Spacing.extraLarge))

    LoginLegalFooter()
  }
}
