package dev.fanfly.wingslog.feature.login

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.AviationBlue10
import dev.fanfly.wingslog.core.ui.theme.AviationBlue80
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.core.sharedassets.generated.resources.app_name
import wingslog.core.sharedassets.generated.resources.ic_launcher_foreground
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.apple_logo
import wingslog.feature.login.generated.resources.continue_without_account
import wingslog.feature.login.generated.resources.google_logo
import wingslog.feature.login.generated.resources.ic_apple
import wingslog.feature.login.generated.resources.ic_google_rd_na
import wingslog.feature.login.generated.resources.legal_disclaimer
import wingslog.feature.login.generated.resources.login_prompt
import wingslog.feature.login.generated.resources.mission_statement
import wingslog.feature.login.generated.resources.privacy_notice
import wingslog.feature.login.generated.resources.sign_in_anonymous_error
import wingslog.feature.login.generated.resources.sign_in_error
import wingslog.feature.login.generated.resources.sign_in_with_apple
import wingslog.feature.login.generated.resources.sign_in_with_google
import wingslog.core.sharedassets.generated.resources.Res as UiRes

private val LoginBackground = AviationBlue10
private val LoginOnBackground = Color(0xFFF0F4FF)
private val LoginOnBackgroundMuted = Color(0xFF8AAAD4)
private val LoginErrorText = Color(0xFFFF8A80)
private val AppleButtonBackground = Color(0xFF000000)
private val AppleButtonContent = Color(0xFFFFFFFF)

private val LoginButtonLabelStyle = TextStyle(
  fontWeight = FontWeight.SemiBold,
  fontSize = 15.sp,
)
private val LoginSecondaryLabelStyle = TextStyle(fontSize = 15.sp)
private val LoginErrorStyle = TextStyle(fontSize = 13.sp)

@Composable
fun LoginScreen(
  loginViewModel: LoginViewModel = koinViewModel(),
  onLoginSuccess: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var error by remember { mutableStateOf<String?>(null) }
  var isSigningIn by remember { mutableStateOf(false) }
  val signInErrorMessage = stringResource(Res.string.sign_in_error)
  val signInAnonymousErrorMessage =
    stringResource(Res.string.sign_in_anonymous_error)
  val headlineFamily = rememberBrandHeadlineFamily()
  val uriHandler = LocalUriHandler.current

  LaunchedEffect(Unit) {
    scope.launch {
      val credential = loginViewModel.silentLogin()
      if (credential != null) {
        onLoginSuccess()
      }
    }
  }

  val bobTransition = rememberInfiniteTransition(label = "bob")
  val bobY by bobTransition.animateFloat(
    initialValue = 0f,
    targetValue = -6f,
    animationSpec = infiniteRepeatable(
      animation = tween(1700, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "bobY",
  )
  val bobRotation by bobTransition.animateFloat(
    initialValue = -1.5f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1700, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "bobRotation",
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(LoginBackground),
    contentAlignment = Alignment.TopCenter,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            Color(0xFF2A6BC9).copy(alpha = 0.20f),
            Color.Transparent
          ),
          center = Offset(size.width / 2f, size.height * 0.30f),
          radius = size.width * 0.70f,
        ),
      )
    }

    Column(
      modifier = Modifier
        .constrainedContentWidth(ContentWidth.Auth)
        .fillMaxSize()
        .navigationBarsPadding()
        .padding(horizontal = 28.dp),
    ) {
      Spacer(Modifier.height(76.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(240.dp),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(UiRes.drawable.ic_launcher_foreground),
          contentDescription = null,
          modifier = Modifier
            .size(180.dp)
            .offset(y = bobY.dp)
            .rotate(bobRotation),
          tint = AviationBlue80,
        )
      }

      Spacer(Modifier.weight(1f))

      Text(
        text = buildAnnotatedString {
          append(stringResource(UiRes.string.app_name))
          withStyle(SpanStyle(color = AviationBlue80)) {
            append(".")
          }
        },
        style = TextStyle(
          fontFamily = headlineFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 44.sp,
          lineHeight = 46.sp,
          letterSpacing = (-1).sp,
          color = LoginOnBackground,
        ),
      )

      Spacer(Modifier.height(6.dp))

      Text(
        text = stringResource(Res.string.mission_statement),
        style = TextStyle(
          fontWeight = FontWeight.Medium,
          fontSize = 16.sp,
          lineHeight = 22.sp,
          color = LoginOnBackgroundMuted,
        ),
      )

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
          .height(54.dp),
        enabled = !isSigningIn,
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(
          containerColor = LoginOnBackground,
          contentColor = LoginBackground,
          disabledContainerColor = LoginOnBackground.copy(alpha = 0.4f),
          disabledContentColor = LoginBackground.copy(alpha = 0.4f),
        ),
        onClick = {
          scope.launch {
            isSigningIn = true
            try {
              val credential = loginViewModel.login()
              if (credential != null) {
                onLoginSuccess()
              } else {
                error = signInErrorMessage
              }
            } finally {
              isSigningIn = false
            }
          }
        },
      ) {
        if (isSigningIn) {
          CircularProgressIndicator(
            modifier = Modifier.size(Spacing.xLarge),
            strokeWidth = 2.dp,
            color = LoginBackground,
          )
        } else {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          ) {
            Icon(
              painter = painterResource(Res.drawable.ic_google_rd_na),
              contentDescription = stringResource(Res.string.google_logo),
              modifier = Modifier.size(Spacing.xLarge),
              tint = Color.Unspecified,
            )
            Text(
              text = stringResource(Res.string.sign_in_with_google),
              style = LoginButtonLabelStyle,
            )
          }
        }
      }

      Spacer(Modifier.height(Spacing.medium))

      // Continue with Apple — shown on every platform. Backend (signInWithApple) is not wired
      // yet; tapping is a no-op until the Apple provider is implemented per platform.
      Button(
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp),
        enabled = !isSigningIn,
        shape = RoundedCornerShape(Spacing.buttonCornerRadius),
        colors = ButtonDefaults.buttonColors(
          containerColor = AppleButtonBackground,
          contentColor = AppleButtonContent,
          disabledContainerColor = AppleButtonBackground.copy(alpha = 0.4f),
          disabledContentColor = AppleButtonContent.copy(alpha = 0.4f),
        ),
        onClick = {
          // TODO(apple-signin): wire AuthManager.signInWithApple() per platform.
        },
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
          Icon(
            painter = painterResource(Res.drawable.ic_apple),
            contentDescription = stringResource(Res.string.apple_logo),
            modifier = Modifier.size(Spacing.xLarge),
            tint = AppleButtonContent,
          )
          Text(
            text = stringResource(Res.string.sign_in_with_apple),
            style = LoginButtonLabelStyle,
          )
        }
      }

      if (isAnonymousLoginSupported) {
        Spacer(Modifier.height(Spacing.medium))

        OutlinedButton(
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
          enabled = !isSigningIn,
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
              isSigningIn = true
              try {
                val credential = loginViewModel.loginAnonymously()
                if (credential != null) {
                  onLoginSuccess()
                } else {
                  error = signInAnonymousErrorMessage
                }
              } finally {
                isSigningIn = false
              }
            }
          },
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          ) {
            Icon(
              imageVector = Icons.Filled.Person,
              contentDescription = null,
              modifier = Modifier.size(Spacing.xLarge),
            )
            Text(
              text = stringResource(Res.string.continue_without_account),
              style = LoginSecondaryLabelStyle,
            )
          }
        }
      }

      Spacer(Modifier.height(Spacing.large))

      Text(
        text = stringResource(Res.string.legal_disclaimer),
        color = LoginOnBackgroundMuted.copy(alpha = 0.6f),
        style = LoginErrorStyle,
        modifier = Modifier.padding(horizontal = Spacing.extraSmall),
      )

      Spacer(Modifier.height(Spacing.medium))

      Text(
        text = stringResource(Res.string.privacy_notice),
        color = LoginOnBackgroundMuted.copy(alpha = 0.85f),
        style = LoginErrorStyle.copy(textDecoration = TextDecoration.Underline),
        modifier = Modifier
          .padding(horizontal = Spacing.extraSmall)
          .clickable { uriHandler.openUri(privacyPolicyUrl) },
      )

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
}
