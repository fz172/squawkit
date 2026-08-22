package dev.fanfly.wingslog.feature.login.onboarding

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.AviationBlue10
import dev.fanfly.wingslog.core.ui.theme.AviationBlue80
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import org.jetbrains.compose.resources.stringResource
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.onboarding_notifications_body
import wingslog.feature.login.generated.resources.onboarding_notifications_eyebrow
import wingslog.feature.login.generated.resources.onboarding_notifications_headline
import wingslog.core.sharedassets.generated.resources.Res as UiRes
import wingslog.core.sharedassets.generated.resources.continue_action

/**
 * Priming explanation shown once, before the real OS permission dialog — not instead of it —
 * mirroring `AdsConsentExplainerScreen`'s pattern for the same reason: an unexplained system
 * prompt reads as an interruption, one with context in front of it reads as a choice. Stateless
 * and opinion-free about what Continue does; `AuthFlow` owns the actual `NotificationPermission`
 * call, which is what keeps this screen previewable (design §10.2).
 */
@Composable
fun NotificationPrimerScreen(
  onContinue: () -> Unit,
) {
  val headlineFamily = rememberBrandHeadlineFamily()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(AviationBlue10),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            Color(0xFF2A6BC9).copy(alpha = 0.20f),
            Color.Transparent,
          ),
          center = Offset(size.width / 2f, size.height * 0.618f),
          radius = size.width * 0.70f,
        ),
      )
    }

    Column(
      modifier = Modifier
        .constrainedContentWidth(ContentWidth.Auth)
        .fillMaxSize(),
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = stringResource(Res.string.onboarding_notifications_eyebrow),
          textAlign = TextAlign.Center,
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = AviationBlue80,
          ),
        )
        Spacer(Modifier.height(16.dp))

        Text(
          text = stringResource(Res.string.onboarding_notifications_headline),
          textAlign = TextAlign.Center,
          style = TextStyle(
            fontFamily = headlineFamily,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 38.sp,
            letterSpacing = (-0.7).sp,
            color = Color.White,
          ),
        )
        Spacer(Modifier.height(14.dp))

        Text(
          text = stringResource(Res.string.onboarding_notifications_body),
          textAlign = TextAlign.Center,
          modifier = Modifier.widthIn(max = 290.dp),
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = Color.White.copy(alpha = 0.62f),
          ),
        )
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Button(
          onClick = onContinue,
          modifier = Modifier
            .fillMaxWidth()
            .height(Spacing.buttonHeight),
          shape = RoundedCornerShape(Spacing.buttonCornerRadius),
          colors = ButtonDefaults.buttonColors(
            containerColor = AviationBlue80,
            contentColor = AviationBlue10,
          ),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = stringResource(UiRes.string.continue_action),
              style = TextStyle(
                fontFamily = headlineFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
              ),
            )
            Spacer(Modifier.width(Spacing.small))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        Spacer(Modifier.height(Spacing.extraLarge))
      }
    }
  }
}
