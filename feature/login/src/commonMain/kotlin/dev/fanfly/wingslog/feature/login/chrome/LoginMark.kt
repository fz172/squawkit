package dev.fanfly.wingslog.feature.login.chrome

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.brand.ThingHero
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import dev.fanfly.wingslog.core.ui.theme.rememberBrandMonoFamily
import dev.fanfly.wingslog.feature.login.LoginScreen
import dev.fanfly.wingslog.feature.login.email.EmailSignInScreen
import org.jetbrains.compose.resources.stringResource
import wingslog.core.sharedassets.generated.resources.app_name
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.mission_statement
import wingslog.core.sharedassets.generated.resources.Res as UiRes

// Shared visual language for the sign-in surfaces (LoginScreen + EmailSignInScreen).
//
// The brand sits on the page, not inside the card: mark, wordmark and tagline above, then a bordered
// card holding nothing but the sign-in rows. Rows run full-bleed to the card's edges so one border
// does the containing and the hairlines between them read as a single list rather than four separate
// buttons.
//
// No row is styled as the primary one. Every provider is an equal row; the accent fill belongs to
// *interaction* — the row you are pressing, and then the row whose request is running — never to a
// particular provider.
//
// Colours come from MaterialTheme.colorScheme. The design canvas was drawn from this app's aviation
// palette — its #1A5FAE / #A7C8FF primaries and #D5E3FF / #004785 containers are AviationBlue40/80
// and 90/30 — so both artboards fall out of the scheme.

/** The part of the brand name set in the primary colour, on every host. */
private const val BRAND_SUFFIX = "It"

/**
 * The brand block above the card: the plane hero, the wordmark, and the tagline.
 *
 * The mark is [ThingHero] rather than the canvas's static tile — Thing glyphs fly into a crate that
 * becomes the plane. [animate] false shows the resting state, for surfaces reached *from* the login
 * page (the email step), where replaying the sequence would be noise.
 */
@Composable
internal fun LoginMark(animate: Boolean = true) {
  val headlineFamily = rememberBrandHeadlineFamily()
  val monoFamily = rememberBrandMonoFamily()
  val appName = stringResource(UiRes.string.app_name)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(124.dp),
    contentAlignment = Alignment.Center,
  ) {
    ThingHero(
      size = 116.dp,
      tint = MaterialTheme.colorScheme.primary,
      fanTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
      animate = animate,
    )
  }

  Spacer(Modifier.height(Spacing.medium))

  Text(
    text = buildAnnotatedString {
      append(appName.removeSuffix(BRAND_SUFFIX))
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
        append(
          BRAND_SUFFIX
        )
      }
    },
    style = TextStyle(
      fontFamily = headlineFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 34.sp,
      lineHeight = 40.sp,
      letterSpacing = (-0.5).sp,
    ),
    color = MaterialTheme.colorScheme.onSurface,
  )

  Spacer(Modifier.height(Spacing.extraSmall))

  Text(
    // Set in caps as a mono label rather than stored shouting, so the string stays a sentence for
    // every other surface that reads it.
    text = stringResource(Res.string.mission_statement).uppercase(),
    style = TextStyle(
      fontFamily = monoFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 11.sp,
      letterSpacing = 1.2.sp,
    ),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
