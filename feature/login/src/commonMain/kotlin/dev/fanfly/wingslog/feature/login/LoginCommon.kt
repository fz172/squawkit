package dev.fanfly.wingslog.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.ui.brand.ThingHero
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.core.sharedassets.generated.resources.app_name
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.legal_disclaimer
import wingslog.feature.login.generated.resources.mission_statement
import wingslog.feature.login.generated.resources.privacy_notice
import wingslog.feature.login.generated.resources.support_link
import wingslog.core.sharedassets.generated.resources.Res as UiRes

// Shared visual language for the sign-in surfaces (LoginScreen + EmailSignInScreen) so the card,
// buttons and footer are identical across Android, iOS, and web.
//
// Everything here reads MaterialTheme.colorScheme rather than fixed colours: the design canvas was
// drawn from this app's own aviation palette (its #1A5FAE / #A7C8FF primaries and #D5E3FF / #004785
// containers are AviationBlue40/80 and 90/30), so the light and dark artboards both fall out of the
// scheme without a second set of constants to keep in step.

/**
 * Apple's button colours for the *upgrade* sheet, which still uses a black Apple button.
 *
 * The login card does not: the design canvas gives every provider the same neutral row, so
 * `LoginScreen` tints the Apple mark with `onSurface` instead. These stay for `feature/login/upgrade`.
 */
internal val AppleButtonBackground = Color(0xFF000000)
internal val AppleButtonContent = Color(0xFFFFFFFF)

/** The part of the brand name set in the primary colour, on every host. */
private const val BRAND_SUFFIX = "It"

internal val LoginButtonLabelStyle = TextStyle(
  fontWeight = FontWeight.SemiBold,
  fontSize = 15.sp,
)
internal val LoginSecondaryLabelStyle = TextStyle(fontSize = 15.sp)
internal val LoginErrorStyle = TextStyle(fontSize = 13.sp)

/** Every sign-in button, on the login page and in the upgrade sheet, is this tall. */
internal val LoginButtonHeight = 56.dp

/** The card never grows past this, however wide the window is. */
internal val LoginCardWidth = 452.dp

internal val LoginCardShape = RoundedCornerShape(20.dp)
internal val LoginButtonShape = RoundedCornerShape(16.dp)

/**
 * The inside of a sign-in button: leading icon, then the label.
 *
 * Shared because the alignment is the whole point. Each button used to wrap its own centred `Row`,
 * which put the icon at a different x in every button — the icon's position depended on the label's
 * width, so a column of them read as ragged. Here the icon is pinned to the leading edge and the
 * label is centred in what is left, balanced by a spacer the icon's width so the label still sits in
 * the middle of the button rather than off to the right.
 *
 * Public, not internal, because the upgrade sheet has its own buttons with the same defect. Keep
 * the signature stable — `feature/login/upgrade` calls it too.
 *
 * [iconSize] must match what [icon] actually renders: it sizes the trailing spacer, and if the two
 * disagree the label stops being centred, which is the bug this exists to prevent.
 */
@Composable
fun LoginButtonContent(
  label: String,
  labelStyle: TextStyle = LoginButtonLabelStyle,
  iconSize: Dp = Spacing.xLarge,
  trailing: @Composable (() -> Unit)? = null,
  icon: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    icon()
    Text(
      text = label,
      style = labelStyle,
      textAlign = TextAlign.Center,
      maxLines = 1,
      modifier = Modifier
        .weight(1f)
        .padding(horizontal = Spacing.small),
    )
    if (trailing != null) trailing() else Spacer(Modifier.size(iconSize))
  }
}

/**
 * The page every sign-in surface sits on: a plain background with the card centred in it, and an
 * optional top bar above.
 *
 * The card scrolls rather than the page: a short window (a phone in landscape, a small browser)
 * must still reach the buttons, and the top bar should stay put while it does.
 */
@Composable
internal fun LoginScaffold(
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = Spacing.large, vertical = Spacing.extraLarge),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = LoginCardWidth)
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface, LoginCardShape)
          .border(
            Spacing.hairline,
            MaterialTheme.colorScheme.outlineVariant,
            LoginCardShape,
          )
          .padding(horizontal = 32.dp, vertical = 32.dp)
          .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
      )
    }
  }
}

/**
 * The brand mark at the top of the card: the plane hero, the wordmark, and the mission statement.
 *
 * The mark is [ThingHero] rather than a static icon — Thing glyphs fly into a crate that becomes
 * the plane. [animate] false shows the resting state, for surfaces reached *from* the login page
 * (the email step), where replaying the sequence would be noise.
 */
@Composable
internal fun LoginMark(animate: Boolean = true) {
  val headlineFamily = rememberBrandHeadlineFamily()
  val appName = stringResource(UiRes.string.app_name)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(132.dp),
    contentAlignment = Alignment.Center,
  ) {
    ThingHero(
      size = 124.dp,
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
      fontSize = 28.sp,
      lineHeight = 32.sp,
      letterSpacing = (-0.5).sp,
    ),
    color = MaterialTheme.colorScheme.onPrimaryContainer,
  )

  Spacer(Modifier.height(Spacing.extraSmall))

  Text(
    text = stringResource(Res.string.mission_statement),
    style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** The disclaimer plus the Terms & Privacy / Support links at the bottom of the card. */
@Composable
internal fun LoginLegalFooter() {
  val uriHandler = LocalUriHandler.current
  val appCapability: AppCapability = koinInject()

  Text(
    text = stringResource(Res.string.legal_disclaimer),
    style = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
  )

  Spacer(Modifier.height(Spacing.medium))

  Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
    Text(
      text = stringResource(Res.string.privacy_notice),
      style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.clickable { uriHandler.openUri(privacyPolicyUrl) },
    )
    appCapability.supportUrl?.let { support ->
      Text(
        text = stringResource(Res.string.support_link),
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { uriHandler.openUri(support) },
      )
    }
  }
}
