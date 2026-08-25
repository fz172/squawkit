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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.adaptive.compose.ContentWidth
import dev.fanfly.wingslog.core.ui.adaptive.compose.constrainedContentWidth
import dev.fanfly.wingslog.core.ui.theme.AviationBlue80
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.continue_action
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.onboarding_ads_consent_body
import wingslog.feature.login.generated.resources.onboarding_ads_consent_eyebrow
import wingslog.feature.login.generated.resources.onboarding_ads_consent_headline
import wingslog.feature.login.generated.resources.onboarding_ads_consent_pro_note
import wingslog.core.sharedassets.generated.resources.Res as UiRes

// Local to this screen — a deliberately distinct navy/gradient from the rest of onboarding
// (Ads Consent redesign, claude.ai/design project 5348edcc-f60d-4878-8893-2c567a424944), not yet
// promoted to core/ui/theme's Color.kt tokens. AviationBlue80 is reused where the design's own hex
// happens to already match it exactly (the eyebrow/pro-note-highlight color).
private val CardBackground = Color(0xFF0B2452)
private val GlowInner = Color(0xFF2D65B4)
private val GlowMid = Color(0xFF1C4A90)
private val BodyText = Color(0xFF9FB2CE)
private val ProNoteText = Color(0xFF7E93B4)
private val ButtonBackground = Color(0xFFAFC9FF)
private val ButtonContent = Color(0xFF0B2452)

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
      .background(CardBackground),
    contentAlignment = Alignment.TopCenter,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      drawCircle(
        brush = Brush.radialGradient(
          colorStops = arrayOf(
            0.0f to GlowInner.copy(alpha = 0.55f),
            0.45f to GlowMid.copy(alpha = 0.30f),
            0.70f to Color.Transparent,
          ),
          center = Offset(size.width / 2f, size.height * 0.52f),
          radius = minOf(size.width, ContentWidth.Auth.value) * 2.1f,
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
          text = stringResource(Res.string.onboarding_ads_consent_eyebrow),
          textAlign = TextAlign.Center,
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
            color = AviationBlue80,
          ),
        )
        Spacer(Modifier.height(16.dp))

        Text(
          text = stringResource(Res.string.onboarding_ads_consent_headline),
          textAlign = TextAlign.Center,
          style = TextStyle(
            fontFamily = headlineFamily,
            fontSize = 46.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 50.sp,
            letterSpacing = (-1.2).sp,
            color = Color.White,
          ),
        )
        Spacer(Modifier.height(18.dp))

        Text(
          text = stringResource(Res.string.onboarding_ads_consent_body),
          textAlign = TextAlign.Center,
          modifier = Modifier.widthIn(max = 290.dp),
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 17.sp,
            lineHeight = 26.sp,
            color = BodyText,
          ),
        )
        Spacer(Modifier.height(14.dp))

        val proNoteRaw = stringResource(Res.string.onboarding_ads_consent_pro_note)
        Text(
          text = highlightBrand(proNoteRaw),
          textAlign = TextAlign.Center,
          style = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontSize = 15.sp,
            color = ProNoteText,
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
            .height(60.dp),
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = ButtonBackground,
            contentColor = ButtonContent,
          ),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = stringResource(UiRes.string.continue_action),
              style = TextStyle(
                fontFamily = headlineFamily,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
              ),
            )
            Spacer(Modifier.width(12.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
          }
        }

        Spacer(Modifier.height(32.dp))
      }
    }
  }
}

/** "SquawkIt Pro" is a proper noun, never localized, so a literal match is safe across locales. */
private fun highlightBrand(text: String) = buildAnnotatedString {
  val marker = "SquawkIt Pro"
  val index = text.indexOf(marker)
  if (index == -1) {
    append(text)
    return@buildAnnotatedString
  }
  append(text.substring(0, index))
  withStyle(SpanStyle(color = AviationBlue80, fontWeight = FontWeight.Medium)) {
    append(marker)
  }
  append(text.substring(index + marker.length))
}
