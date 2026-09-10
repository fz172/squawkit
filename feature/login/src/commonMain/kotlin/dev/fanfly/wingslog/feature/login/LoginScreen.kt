package dev.fanfly.wingslog.feature.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

/**
 * The sign-in card, shared by Android, iOS and web.
 *
 * Every provider is one row in a single column of identical buttons — the design canvas draws them
 * that way deliberately: none of Google, Apple or email is the "real" option with the others as
 * fallbacks, so none of them is styled as the primary action.
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
  val signIn = { provider: PendingSignIn, failure: String, request: suspend () -> Any? ->
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

  LoginScaffold(topBar = { LoginTopBar() }) {
    LoginMark()

    Spacer(Modifier.height(Spacing.extraLarge))

    LoginProviderButton(
      label = stringResource(Res.string.sign_in_with_google),
      enabled = signingIn == null,
      loading = signingIn == PendingSignIn.Google,
      onClick = {
        signIn(PendingSignIn.Google, signInErrorMessage) { loginViewModel.login() }
      },
      icon = {
        Icon(
          painter = painterResource(Res.drawable.ic_google_rd_na),
          contentDescription = stringResource(Res.string.google_logo),
          modifier = Modifier.size(Spacing.xLarge),
          tint = Color.Unspecified,
        )
      },
    )

    // Continue with Apple — offered on every platform since #408 gave Android its Custom Tab flow.
    Spacer(Modifier.height(Spacing.medium))

    LoginProviderButton(
      label = stringResource(Res.string.sign_in_with_apple),
      enabled = signingIn == null,
      loading = signingIn == PendingSignIn.Apple,
      onClick = {
        signIn(PendingSignIn.Apple, signInErrorMessage) { loginViewModel.loginWithApple() }
      },
      icon = {
        Icon(
          painter = painterResource(Res.drawable.ic_apple),
          contentDescription = stringResource(Res.string.apple_logo),
          modifier = Modifier.size(Spacing.xLarge),
          tint = MaterialTheme.colorScheme.onSurface,
        )
      },
    )

    Spacer(Modifier.height(Spacing.medium))

    // Passwordless email link — the neutral third option. Navigates to the shared Email Sign-In
    // page (same on every platform); see EmailSignInScreen. The chevron says so: this one goes
    // somewhere rather than starting a request here.
    LoginProviderButton(
      label = stringResource(Res.string.sign_in_with_email),
      enabled = signingIn == null,
      loading = false,
      onClick = onChooseEmail,
      icon = {
        Icon(
          imageVector = Icons.Filled.Email,
          contentDescription = null,
          modifier = Modifier.size(Spacing.xLarge),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      trailing = {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          modifier = Modifier.size(Spacing.xLarge),
          tint = MaterialTheme.colorScheme.outline,
        )
      },
    )

    // Web sets this false (AppCapability.js.kt): an anonymous account cannot be carried between
    // browsers, so the option would strand records the user cannot get back.
    if (appCapability.isAnonymousLoginSupported) {
      Spacer(Modifier.height(Spacing.medium))

      LoginProviderButton(
        label = stringResource(Res.string.continue_without_account),
        labelStyle = LoginSecondaryLabelStyle,
        enabled = signingIn == null,
        loading = signingIn == PendingSignIn.Anonymous,
        muted = true,
        onClick = {
          signIn(PendingSignIn.Anonymous, signInAnonymousErrorMessage) {
            loginViewModel.loginAnonymously()
          }
        },
        icon = {
          Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier.size(Spacing.xLarge),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
      )
    }

    error?.let {
      Spacer(Modifier.height(Spacing.medium))
      Text(
        text = it,
        color = MaterialTheme.colorScheme.error,
        style = LoginErrorStyle,
        textAlign = TextAlign.Center,
      )
    }

    Spacer(Modifier.height(Spacing.extraLarge))

    LoginLegalFooter()
  }
}

/**
 * One row of the provider column. Identical geometry for every provider — the only differences are
 * the icon, the label, and whether it is the muted anonymous option.
 */
@Composable
private fun LoginProviderButton(
  label: String,
  enabled: Boolean,
  loading: Boolean,
  onClick: () -> Unit,
  icon: @Composable () -> Unit,
  labelStyle: TextStyle = LoginButtonLabelStyle,
  muted: Boolean = false,
  trailing: @Composable (() -> Unit)? = null,
) {
  val content = if (muted) {
    MaterialTheme.colorScheme.onSurfaceVariant
  } else {
    MaterialTheme.colorScheme.onPrimaryContainer
  }
  val container = if (muted) {
    MaterialTheme.colorScheme.surface
  } else {
    MaterialTheme.colorScheme.primaryContainer
  }

  OutlinedButton(
    modifier = Modifier
      .fillMaxWidth()
      .height(LoginButtonHeight),
    enabled = enabled,
    shape = LoginButtonShape,
    contentPadding = PaddingDefaults,
    // A tonal fill and a little lift, rather than an outline on the card's own colour: on white the
    // outlined form read as a placeholder rather than the page's primary thing to do. All three
    // providers keep the same treatment — none of them is the "real" option (see the KDoc above).
    elevation = ButtonDefaults.buttonElevation(
      defaultElevation = if (muted) 0.dp else 1.dp,
      pressedElevation = 0.dp,
      disabledElevation = 0.dp,
    ),
    // Every one of these must be named. Material's ButtonColors leaves the ones you omit as
    // Color.Unspecified, which paints *black* — and since a sign-in disables the whole column,
    // omitting the disabled pair turned every other button into a black slab mid-request.
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = container,
      contentColor = content,
      disabledContainerColor = container,
      disabledContentColor = content,
    ),
    border = BorderStroke(
      Spacing.hairline,
      MaterialTheme.colorScheme.outlineVariant.let {
        if (enabled) it else it.copy(alpha = it.alpha * DisabledAlpha)
      },
    ),
    onClick = onClick,
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(Spacing.xLarge),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary,
      )
    } else {
      // Dim the whole row rather than the label alone: each icon sets its own tint (the Google mark
      // keeps its brand colours), so a disabled content colour never reaches them and a busy card
      // ended up with faded text beside full-strength marks.
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .alpha(if (enabled) 1f else DisabledAlpha),
      ) {
        LoginButtonContent(label = label, labelStyle = labelStyle, trailing = trailing, icon = icon)
      }
    }
  }
}

/** The button's own padding; [LoginButtonContent] owns the spacing inside it. */
private val PaddingDefaults = PaddingValues(horizontal = 16.dp)

/** Material's disabled opacity, applied to content and border so a busy card reads as busy, not broken. */
private const val DisabledAlpha = 0.38f
