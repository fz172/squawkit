package dev.fanfly.wingslog.feature.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.apple_logo
import wingslog.feature.login.generated.resources.continue_without_account
import wingslog.feature.login.generated.resources.google_logo
import wingslog.feature.login.generated.resources.ic_apple
import wingslog.feature.login.generated.resources.ic_google_rd_na
import wingslog.feature.login.generated.resources.login_prompt
import wingslog.feature.login.generated.resources.sign_in_anonymous_error
import wingslog.feature.login.generated.resources.sign_in_error
import wingslog.feature.login.generated.resources.sign_in_with_apple
import wingslog.feature.login.generated.resources.sign_in_with_email
import wingslog.feature.login.generated.resources.sign_in_with_google

/**
 * Which sign-in request is awaiting a result, so only the pressed button spins while the rest are
 * disabled. Null means idle.
 *
 * Not the list of login methods on offer — only those that suspend *here*. The email option
 * navigates away to `EmailSignInScreen`, which owns the progress state for both legs of the link
 * flow, so it never reaches an in-flight state on this screen.
 */
private enum class PendingSignIn { Google, Apple, Anonymous }

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

  LoginBackdrop {
    Spacer(Modifier.height(76.dp))

    LoginPlaneArt()

    Spacer(Modifier.weight(1f))

    LoginWordmark()

    Spacer(Modifier.height(Spacing.huge))

    Text(
      text = stringResource(Res.string.login_prompt),
      style = TextStyle(
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
        color = LoginOnBackgroundMuted.copy(alpha = 0.7f),
      ),
    )

    Spacer(Modifier.height(Spacing.extraLarge))

    Button(
      modifier = Modifier
        .fillMaxWidth()
        .height(LoginButtonHeight),
      enabled = signingIn == null,
      shape = RoundedCornerShape(Spacing.buttonCornerRadius),
      colors = ButtonDefaults.buttonColors(
        containerColor = LoginOnBackground,
        contentColor = LoginBackground,
        disabledContainerColor = LoginOnBackground.copy(alpha = 0.4f),
        disabledContentColor = LoginBackground.copy(alpha = 0.4f),
      ),
      onClick = {
        scope.launch {
          signingIn = PendingSignIn.Google
          try {
            val credential = loginViewModel.login()
            if (credential != null) {
              onLoginSuccess()
            } else {
              error = signInErrorMessage
            }
          } finally {
            signingIn = null
          }
        }
      },
    ) {
      if (signingIn == PendingSignIn.Google) {
        CircularProgressIndicator(
          modifier = Modifier.size(Spacing.xLarge),
          strokeWidth = 2.dp,
          color = LoginBackground,
        )
      } else {
        LoginButtonContent(label = stringResource(Res.string.sign_in_with_google)) {
          Icon(
            painter = painterResource(Res.drawable.ic_google_rd_na),
            contentDescription = stringResource(Res.string.google_logo),
            modifier = Modifier.size(Spacing.xLarge),
            tint = Color.Unspecified,
          )
        }
      }
    }

    // Continue with Apple. Still behind AppCapability.isAppleSignInSupported even though every
    // platform now sets it (Android gained the flow in #408) — the gate describes a capability, and
    // erasing it would mean rediscovering which platforms can do this the next time one cannot.
    if (appCapability.isAppleSignInSupported) {
      Spacer(Modifier.height(Spacing.medium))

      Button(
        modifier = Modifier
          .fillMaxWidth()
          .height(LoginButtonHeight),
        enabled = signingIn == null,
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(
          containerColor = AppleButtonBackground,
          contentColor = AppleButtonContent,
          disabledContainerColor = AppleButtonBackground.copy(alpha = 0.4f),
          disabledContentColor = AppleButtonContent.copy(alpha = 0.4f),
        ),
        onClick = {
          scope.launch {
            signingIn = PendingSignIn.Apple
            try {
              val credential = loginViewModel.loginWithApple()
              if (credential != null) {
                onLoginSuccess()
              } else {
                error = signInErrorMessage
              }
            } finally {
              signingIn = null
            }
          }
        },
      ) {
        if (signingIn == PendingSignIn.Apple) {
          CircularProgressIndicator(
            modifier = Modifier.size(Spacing.xLarge),
            strokeWidth = 2.dp,
            color = AppleButtonContent,
          )
        } else {
          LoginButtonContent(label = stringResource(Res.string.sign_in_with_apple)) {
            Icon(
              painter = painterResource(Res.drawable.ic_apple),
              contentDescription = stringResource(Res.string.apple_logo),
              modifier = Modifier.size(Spacing.xLarge),
              tint = AppleButtonContent,
            )
          }
        }
      }
    }

    Spacer(Modifier.height(Spacing.medium))

    // Passwordless email link — the neutral third option. Navigates to the shared Email Sign-In
    // page (same on every platform); see EmailSignInScreen.
    OutlinedButton(
      modifier = Modifier
        .fillMaxWidth()
        .height(LoginButtonHeight),
      enabled = signingIn == null,
      shape = RoundedCornerShape(Spacing.buttonCornerRadius),
      colors = ButtonDefaults.outlinedButtonColors(
        contentColor = LoginOnBackground,
      ),
      border = BorderStroke(
        Spacing.hairline,
        LoginOnBackground.copy(alpha = 0.5f),
      ),
      onClick = onChooseEmail,
    ) {
      LoginButtonContent(label = stringResource(Res.string.sign_in_with_email)) {
        Icon(
          imageVector = Icons.Filled.Email,
          contentDescription = null,
          modifier = Modifier.size(Spacing.xLarge),
        )
      }
    }

    if (appCapability.isAnonymousLoginSupported) {
      Spacer(Modifier.height(Spacing.medium))

      OutlinedButton(
        modifier = Modifier
          .fillMaxWidth()
          .height(LoginButtonHeight),
        enabled = signingIn == null,
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = LoginOnBackgroundMuted,
        ),
        border = BorderStroke(
          Spacing.hairline,
          LoginOnBackgroundMuted.copy(alpha = 0.4f),
        ),
        onClick = {
          scope.launch {
            signingIn = PendingSignIn.Anonymous
            try {
              val credential = loginViewModel.loginAnonymously()
              if (credential != null) {
                onLoginSuccess()
              } else {
                error = signInAnonymousErrorMessage
              }
            } finally {
              signingIn = null
            }
          }
        },
      ) {
        LoginButtonContent(
          label = stringResource(Res.string.continue_without_account),
          labelStyle = LoginSecondaryLabelStyle,
        ) {
          Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier.size(Spacing.xLarge),
          )
        }
      }
    }

    Spacer(Modifier.height(Spacing.large))

    LoginLegalFooter()

    Spacer(Modifier.height(Spacing.large))

    error?.let {
      Text(
        text = it,
        color = LoginErrorText,
        style = LoginErrorStyle,
        modifier = Modifier.padding(bottom = Spacing.large),
      )
    }
  }
}
