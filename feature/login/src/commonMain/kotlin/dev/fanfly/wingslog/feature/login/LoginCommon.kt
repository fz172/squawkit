package dev.fanfly.wingslog.feature.login

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.AviationBlue10
import dev.fanfly.wingslog.core.ui.theme.AviationBlue80
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.app_name
import wingslog.core.sharedassets.generated.resources.ic_launcher_foreground
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.legal_disclaimer
import wingslog.feature.login.generated.resources.mission_statement
import wingslog.feature.login.generated.resources.privacy_notice
import wingslog.core.sharedassets.generated.resources.Res as UiRes

// Shared visual language for the sign-in surfaces (LoginScreen + EmailSignInScreen) so the header,
// background, and footer are pixel-identical across Android, iOS, and web.
internal val LoginBackground = AviationBlue10
internal val LoginOnBackground = Color(0xFFF0F4FF)
internal val LoginOnBackgroundMuted = Color(0xFF8AAAD4)
internal val LoginErrorText = Color(0xFFFF8A80)
internal val AppleButtonBackground = Color(0xFF000000)
internal val AppleButtonContent = Color(0xFFFFFFFF)

internal val LoginButtonLabelStyle = TextStyle(
  fontWeight = FontWeight.SemiBold,
  fontSize = 15.sp,
)
internal val LoginSecondaryLabelStyle = TextStyle(fontSize = 15.sp)
internal val LoginErrorStyle = TextStyle(fontSize = 13.sp)

/**
 * The navy backdrop + soft radial glow + content column shared by every sign-in screen. [content]
 * is laid out in a full-height column with the standard auth content width and horizontal padding.
 */
@Composable
internal fun LoginBackdrop(content: @Composable ColumnScope.() -> Unit) {
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
            Color.Transparent,
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
      content = content,
    )
  }
}

/** The bobbing brand-plane mark at the top of the sign-in surfaces. */
@Composable
internal fun LoginPlaneArt() {
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
}

/** The "SquawkIt." wordmark with the blue trailing dot, plus the mission statement beneath it. */
@Composable
internal fun LoginWordmark() {
  val headlineFamily = rememberBrandHeadlineFamily()
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
}

/** The legal disclaimer + tappable privacy notice shared at the bottom of the sign-in surfaces. */
@Composable
internal fun LoginLegalFooter() {
  val uriHandler = LocalUriHandler.current
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
}
