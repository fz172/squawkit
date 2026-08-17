package dev.fanfly.wingslog.feature.login.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Campaign
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.AviationBlue10
import dev.fanfly.wingslog.core.ui.theme.AviationBlue80
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.continue_action
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.onboarding_ads_consent_body
import wingslog.feature.login.generated.resources.onboarding_ads_consent_eyebrow
import wingslog.feature.login.generated.resources.onboarding_ads_consent_headline
import wingslog.core.sharedassets.generated.resources.Res as UiRes

/**
 * Priming explanation shown once, before the real Google CMP dialog — not instead of it — for a
 * free-tier pilot in a region requiring a privacy choice. Standing this in front of the system
 * dialog (rather than letting `AdSlot` trigger it unannounced mid-scroll on the first ad-eligible
 * list) is Google's own recommended pattern for exactly this reason: an unexplained system dialog
 * reads as an interruption, one with context in front of it reads as a choice. [onContinue] is
 * responsible for actually presenting the CMP (`AdConsentManager.presentConsentForm()`) — this
 * screen has no opinion on what happens after, including if there turns out to be nothing to show
 * (a region not requiring one, resolved between the background check and this tap).
 */
@Composable
fun AdsConsentExplainerScreen(
  onContinue: () -> Unit,
) {
  val headlineFamily = rememberBrandHeadlineFamily()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(AviationBlue10),
    contentAlignment = Alignment.TopCenter,
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
        .fillMaxSize()
        .padding(start = 28.dp, end = 28.dp, top = 96.dp),
    ) {
      Box(
        modifier = Modifier
          .size(72.dp)
          .background(Color.White.copy(alpha = 0.06f), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Default.Campaign,
          contentDescription = null,
          modifier = Modifier.size(32.dp),
          tint = AviationBlue80,
        )
      }

      Spacer(Modifier.height(24.dp))

      Text(
        text = stringResource(Res.string.onboarding_ads_consent_eyebrow),
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.5.sp,
          color = AviationBlue80,
        ),
      )
      Spacer(Modifier.height(8.dp))

      Text(
        text = stringResource(Res.string.onboarding_ads_consent_headline),
        style = TextStyle(
          fontFamily = headlineFamily,
          fontSize = 30.sp,
          fontWeight = FontWeight.Bold,
          lineHeight = 34.sp,
          letterSpacing = (-0.5).sp,
          color = Color.White,
        ),
      )
      Spacer(Modifier.height(10.dp))

      Text(
        text = stringResource(Res.string.onboarding_ads_consent_body),
        style = TextStyle(
          fontFamily = FontFamily.SansSerif,
          fontSize = 14.sp,
          lineHeight = 20.sp,
          color = Color.White.copy(alpha = 0.62f),
        ),
      )

      Spacer(Modifier.weight(1f))

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
