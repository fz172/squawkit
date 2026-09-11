package dev.fanfly.wingslog.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.ui.brand.ThingHero
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import dev.fanfly.wingslog.core.ui.theme.rememberBrandMonoFamily
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import wingslog.core.sharedassets.generated.resources.app_name
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.legal_disclaimer
import wingslog.feature.login.generated.resources.mission_statement
import wingslog.feature.login.generated.resources.privacy_notice
import wingslog.feature.login.generated.resources.support_link
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

/** Material's disabled opacity, used for the rows a running request has locked. */
internal const val DisabledAlpha = 0.38f

/** Every sign-in row is this tall. */
internal val LoginRowHeight = 60.dp

/** The icon column, so labels line up whatever mark sits beside them. */
private val RowIconSize = 26.dp

private val RowPadding = 24.dp

/** The card, and the column it sits in, never grow past this. */
internal val LoginCardWidth = 400.dp

internal val LoginCardShape = RoundedCornerShape(12.dp)

internal val LoginErrorStyle = TextStyle(fontSize = 13.sp, lineHeight = 19.sp)

/**
 * True when the resolved scheme is a dark one.
 *
 * The accent row needs a mid-dark blue in *both* themes — `primary` in light (#1A5FAE) and
 * `primaryContainer` in dark (#004785) — because each theme's other role is far too light to carry
 * white text. No single role does that, so the scheme is asked directly rather than reading the
 * app's appearance setting, which would be a second source of truth for the same fact.
 */
@Composable
@ReadOnlyComposable
private fun isDarkScheme(): Boolean =
  MaterialTheme.colorScheme.surface.luminance() < 0.5f

/** The pressed/active row's background: the strongest blue each theme has that white text sits on. */
@Composable
@ReadOnlyComposable
internal fun accentRowColor(): Color = if (isDarkScheme()) {
  MaterialTheme.colorScheme.primaryContainer
} else {
  MaterialTheme.colorScheme.primary
}

/** Its chevron: the *other* blue, so it reads as a hint rather than a second label. */
@Composable
@ReadOnlyComposable
internal fun accentRowChevronColor(): Color = if (isDarkScheme()) {
  MaterialTheme.colorScheme.primary
} else {
  MaterialTheme.colorScheme.primaryContainer
}

/**
 * The page every sign-in surface sits on, with its content centred in a single column.
 *
 * The column scrolls rather than the page: a short window (a phone in landscape, a small browser)
 * must still reach the last row.
 */
@Composable
internal fun LoginScaffold(content: @Composable ColumnScope.() -> Unit) {
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
        .padding(horizontal = RowPadding, vertical = Spacing.extraLarge),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = LoginCardWidth)
          .fillMaxWidth()
          .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
      )
    }
  }
}

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

/**
 * The bordered card holding the sign-in rows: a small "SIGN IN" heading, an optional status on the
 * right, then the rows themselves.
 *
 * [content] should be [LoginRow]s separated by [LoginRowDivider]; they are clipped to the card so a
 * pressed row's corners follow the border rather than squaring it off.
 */
@Composable
internal fun LoginCard(
  heading: String? = null,
  status: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val monoFamily = rememberBrandMonoFamily()
  val labelStyle = TextStyle(
    fontFamily = monoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
    letterSpacing = 1.2.sp,
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(LoginCardShape)
      .background(MaterialTheme.colorScheme.surface)
      .border(
        Spacing.hairline,
        MaterialTheme.colorScheme.outlineVariant,
        LoginCardShape
      ),
  ) {
    // Omitted where the surface already says what the rows are for — the upgrade sheet has its own
    // title above the card, and a second "SIGN IN" under it would just be a label on a label.
    if (heading != null || status != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            start = RowPadding,
            end = RowPadding,
            top = 20.dp,
            bottom = 12.dp
          ),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = heading.orEmpty(),
          style = labelStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (status != null) {
          Text(
            text = status,
            style = labelStyle,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
      LoginRowDivider()
    }
    content()
  }
}

/** The hairline between two rows, full-bleed like the rows themselves. */
@Composable
internal fun LoginRowDivider() {
  HorizontalDivider(
    thickness = Spacing.hairline,
    color = MaterialTheme.colorScheme.outlineVariant,
  )
}

/**
 * One sign-in row: the provider's mark, its name, and a chevron.
 *
 * The accent fill belongs to interaction, not to a provider: a row takes it while it is *pressed*,
 * and keeps it while [inProgress] — where it also swaps the chevron for a spinner and grows a
 * progress line along its bottom edge. At rest every row looks the same, which is the point.
 *
 * A row the card has locked dims as a whole rather than by content colour: each mark sets its own
 * tint (the Google mark keeps its brand colours), so a disabled colour would never reach it and the
 * row would show faded text beside a full-strength icon.
 */
@Composable
internal fun LoginRow(
  label: String,
  enabled: Boolean,
  onClick: () -> Unit,
  icon: @Composable () -> Unit,
  inProgress: Boolean = false,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val accented = pressed || inProgress

  val background = if (accented) accentRowColor() else Color.Transparent
  val content =
    if (accented) Color.White else MaterialTheme.colorScheme.onSurface
  val chevron =
    if (accented) accentRowChevronColor() else MaterialTheme.colorScheme.onSurfaceVariant

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(LoginRowHeight)
      .background(background)
      .then(
        if (enabled) {
          Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
          )
        } else {
          Modifier
        },
      ),
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = RowPadding)
        .alpha(if (enabled || inProgress) 1f else DisabledAlpha),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(
        modifier = Modifier.size(RowIconSize),
        contentAlignment = Alignment.Center,
        content = { icon() },
      )
      Text(
        text = label,
        modifier = Modifier.weight(1f),
        style = TextStyle(
          fontFamily = rememberBrandHeadlineFamily(),
          fontWeight = FontWeight.SemiBold,
          fontSize = 16.sp,
        ),
        color = content,
        maxLines = 1,
      )
      LoginRowTrailing(inProgress = inProgress, chevron = chevron)
    }

    if (inProgress) {
      LinearProgressIndicator(
        modifier = Modifier
          .fillMaxWidth()
          .height(2.dp)
          .align(Alignment.BottomCenter),
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.22f),
      )
    }
  }
}

/** The disclaimer plus the Terms & Privacy / Support links, centred under the card. */
@Composable
internal fun LoginLegalFooter() {
  val uriHandler = LocalUriHandler.current
  val appCapability: AppCapability = koinInject()

  Text(
    text = stringResource(Res.string.legal_disclaimer),
    style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
  )

  Spacer(Modifier.height(14.dp))

  Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
    Text(
      text = stringResource(Res.string.privacy_notice),
      style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.clickable { uriHandler.openUri(privacyPolicyUrl) },
    )
    appCapability.supportUrl?.let { support ->
      Text(
        text = stringResource(Res.string.support_link),
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { uriHandler.openUri(support) },
      )
    }
  }
}
