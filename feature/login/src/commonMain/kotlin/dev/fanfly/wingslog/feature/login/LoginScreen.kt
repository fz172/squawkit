package dev.fanfly.wingslog.feature.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.login.chrome.LoginCard
import dev.fanfly.wingslog.feature.login.chrome.LoginLegalFooter
import dev.fanfly.wingslog.feature.login.chrome.LoginMark
import dev.fanfly.wingslog.feature.login.chrome.LoginRow
import dev.fanfly.wingslog.feature.login.chrome.LoginRowDivider
import dev.fanfly.wingslog.feature.login.chrome.LoginScaffold
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.apple_logo
import wingslog.feature.login.generated.resources.continue_as_guest
import wingslog.feature.login.generated.resources.continuing_as_guest
import wingslog.feature.login.generated.resources.ic_apple
import wingslog.feature.login.generated.resources.provider_apple
import wingslog.feature.login.generated.resources.provider_email
import wingslog.feature.login.generated.resources.provider_google
import wingslog.feature.login.generated.resources.sign_in_anonymous_error
import wingslog.feature.login.generated.resources.sign_in_connecting
import wingslog.feature.login.generated.resources.sign_in_error
import wingslog.feature.login.generated.resources.sign_in_section_label
import wingslog.feature.login.generated.resources.signing_in_with

/**
 * The sign-in card, shared by Android, iOS and web.
 *
 * The brand sits above the card and the card holds only the rows. Every provider is one row of a
 * single list, identical at rest — none of Google, Apple or email is the "real" option with the
 * others as fallbacks, so none carries a permanent accent. The accent belongs to interaction: the
 * row being pressed, and then the row whose request is running.
 */
@Composable
fun LoginScreen(
  loginViewModel: LoginViewModel = koinViewModel(),
  onLoginSuccess: () -> Unit,
  onChooseEmail: () -> Unit,
) {
  val appCapability: AppCapability = koinInject()
  val scope = rememberCoroutineScope()
  var error by remember { mutableStateOf<String?>(null) }
  var signingIn by remember { mutableStateOf<PendingSignIn?>(null) }
  val signInErrorMessage = stringResource(Res.string.sign_in_error)
  val signInAnonymousErrorMessage =
    stringResource(Res.string.sign_in_anonymous_error)

  LaunchedEffect(Unit) {
    scope.launch {
      val credential = loginViewModel.silentLogin()
      if (credential != null) {
        onLoginSuccess()
      }
    }
  }

  /** Runs one provider's sign-in, holding [signingIn] for the duration so the card locks. */
  val signIn =
    { provider: PendingSignIn, failure: String, request: suspend () -> Any? ->
      scope.launch {
        signingIn = provider
        error = null
        try {
          if (request() != null) onLoginSuccess() else error = failure
        } finally {
          signingIn = null
        }
      }
      Unit
    }

  val idle = signingIn == null
  val google = stringResource(Res.string.provider_google)
  val apple = stringResource(Res.string.provider_apple)
  val guest = stringResource(Res.string.continue_as_guest)

  LoginScaffold {
    LoginMark()

    Spacer(Modifier.height(Spacing.extraLarge))

    LoginCard(
      heading = stringResource(Res.string.sign_in_section_label),
      status = if (idle) null else stringResource(Res.string.sign_in_connecting),
    ) {
      val googleRunning = signingIn == PendingSignIn.Google
      LoginRow(
        label = if (googleRunning) stringResource(
          Res.string.signing_in_with,
          google
        ) else google,
        enabled = idle,
        inProgress = googleRunning,
        onClick = {
          signIn(
            PendingSignIn.Google,
            signInErrorMessage
          ) { loginViewModel.login() }
        },
        icon = {
          // Google's mark keeps its own colours, so it needs a light disc to sit on whenever the row
          // takes the accent fill — which is precisely when it is pressed or connecting.
          GoogleMark(onAccent = googleRunning)
        },
      )

      LoginRowDivider()

      // Continue with Apple — offered on every platform since #408 gave Android its Custom Tab flow.
      val appleRunning = signingIn == PendingSignIn.Apple
      LoginRow(
        label = if (appleRunning) stringResource(
          Res.string.signing_in_with,
          apple
        ) else apple,
        enabled = idle,
        inProgress = appleRunning,
        onClick = {
          signIn(
            PendingSignIn.Apple,
            signInErrorMessage
          ) { loginViewModel.loginWithApple() }
        },
        icon = {
          Icon(
            painter = painterResource(Res.drawable.ic_apple),
            contentDescription = stringResource(Res.string.apple_logo),
            modifier = Modifier.size(18.dp),
            tint = if (appleRunning) Color.White else MaterialTheme.colorScheme.onSurface,
          )
        },
      )

      LoginRowDivider()

      // Passwordless email link — navigates to the shared Email Sign-In page rather than starting a
      // request here, so this row never reaches a progress state.
      LoginRow(
        label = stringResource(Res.string.provider_email),
        enabled = idle,
        onClick = onChooseEmail,
        icon = {
          Icon(
            imageVector = Icons.Filled.Email,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
        },
      )

      // Web sets this false (AppCapability.js.kt): an anonymous account cannot be carried between
      // browsers, so the option would strand records the user cannot get back.
      if (appCapability.isAnonymousLoginSupported) {
        LoginRowDivider()

        // Not "Signing in with Continue as guest": this row is an escape hatch rather than a
        // provider, so it carries its own progress wording instead of the shared frame.
        val anonRunning = signingIn == PendingSignIn.Anonymous
        LoginRow(
          label = if (anonRunning) stringResource(Res.string.continuing_as_guest) else guest,
          enabled = idle,
          inProgress = anonRunning,
          onClick = {
            signIn(PendingSignIn.Anonymous, signInAnonymousErrorMessage) {
              loginViewModel.loginAnonymously()
            }
          },
          icon = {
            Icon(
              imageVector = Icons.Filled.Person,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
          },
        )
      }
    }

    error?.let {
      Spacer(Modifier.height(Spacing.medium))
      LoginAdvisory(it)
    }

    Spacer(Modifier.height(Spacing.large))

    LoginLegalFooter()
  }
}
