package dev.fanfly.wingslog.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.adaptive.compose.layoutTierFor
import dev.fanfly.wingslog.core.ui.adaptive.thingIcon
import dev.fanfly.wingslog.core.ui.brand.BrandPlane
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import dev.fanfly.wingslog.core.ui.theme.rememberBrandMonoFamily
import dev.fanfly.wingslog.feature.login.LoginButtonContent
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.privacy_notice
import kotlin.math.roundToInt

/**
 * Which sign-in request is currently awaiting a result, so the pressed button shows a spinner while
 * the others are disabled. Null means idle.
 *
 * Only the providers that suspend on this page. Email navigates away to the shared
 * `EmailSignInScreen`, which owns its own progress state, and anonymous sign-in does not exist on
 * web (`AppCapability.isAnonymousLoginSupported` is false here).
 */
private enum class PendingSignIn { Google, Apple }

/** The Android app's Google Play listing. */
private const val PlayStoreUrl =
  "https://play.google.com/store/apps/details?id=dev.fanfly.wingslog"

/** The static support page next to the app (`webApp/src/jsMain/resources/support.html`). */
private const val SupportUrl = "/support.html"

/** The App Store listing once the iOS build is approved. Null leaves the button out. */
private val AppStoreUrl: String? = null

/** Show the App Store as a dashed "coming soon" placeholder while [AppStoreUrl] is null. */
private const val ShowAppStoreComingSoon = false

/** The "Now on Android" tile inside the login card. */
private const val ShowHeroPromo = true

/**
 * The web-only SquawkIt sign-in / SEO landing page: sticky header, navy hero with the login card,
 * features, how-it-works, FAQ, Get-the-app, final CTA, footer. Rendered in Compose to match the
 * `Login Page.dc.html` design. Swapped into [dev.fanfly.wingslog.feature.login.AuthFlow] via its
 * `loginContent` slot by [WebApp], so the shared onboarding tail and the real Firebase auth wiring
 * ([LoginViewModel]) are reused unchanged.
 *
 * Web only: the native Android and iOS [dev.fanfly.wingslog.feature.login.LoginScreen] is untouched.
 *
 * Colors and marks come from [WebLandingAssets]. Breakpoints mirror the design's CSS grids via
 * [BoxWithConstraints]; light/dark follows the OS setting.
 */
@Composable
internal fun WebLoginLandingScreen(
  onLoginSuccess: () -> Unit,
  onChooseEmail: () -> Unit,
  loginViewModel: LoginViewModel = koinViewModel(),
) {
  val colors =
    if (isSystemInDarkTheme()) DarkLandingColors else LightLandingColors
  val type = LandingType(
    headline = rememberBrandHeadlineFamily(),
    mono = rememberBrandMonoFamily(),
  )
  val scope = rememberCoroutineScope()
  val scrollState = rememberScrollState()

  var signingIn by remember { mutableStateOf<PendingSignIn?>(null) }
  var error by remember { mutableStateOf<String?>(null) }

  // Returning, already-authenticated users skip straight through (mirrors LoginScreen).
  LaunchedEffect(Unit) {
    val credential = loginViewModel.silentLogin()
    if (credential != null) onLoginSuccess()
  }

  val signIn = { provider: PendingSignIn ->
    scope.launch {
      signingIn = provider
      error = null
      try {
        val credential = when (provider) {
          PendingSignIn.Google -> loginViewModel.login()
          PendingSignIn.Apple -> loginViewModel.loginWithApple()
        }
        if (credential != null) onLoginSuccess() else error = SignInFailed
      } catch (t: Throwable) {
        error = SignInFailed
      } finally {
        signingIn = null
      }
    }
    Unit
  }

  // Section anchors for in-page navigation: each section's offset inside the scroll column, which
  // does not move as the page scrolls (root coordinates would, and are not re-dispatched for
  // sections that are off screen).
  var heroY by remember { mutableStateOf(0f) }
  var featuresY by remember { mutableStateOf(0f) }
  var howY by remember { mutableStateOf(0f) }
  var faqY by remember { mutableStateOf(0f) }
  var appY by remember { mutableStateOf(0f) }
  val scrollTo = { y: Float ->
    scope.launch { scrollState.animateScrollTo(y.roundToInt().coerceAtLeast(0)) }
    Unit
  }
  val anchor = { set: (Float) -> Unit ->
    Modifier.onGloballyPositioned { coordinates ->
      val parent = coordinates.parentLayoutCoordinates ?: return@onGloballyPositioned
      set(parent.localPositionOf(coordinates, Offset.Zero).y)
    }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.surface),
  ) {
    val w = maxWidth
    val compact = layoutTierFor(w).isCompact
    // Below this the hero copy and the login card stack, as do the two-column sections.
    val stacked = w < 880.dp
    val metrics = LandingMetrics(
      heroTitle = (w.value * 0.05f).coerceIn(40f, 60f).sp,
      sectionTitle = (w.value * 0.034f).coerceIn(28f, 40f).sp,
      sectionPadding = if (stacked) 64.dp else 88.dp,
    )

    Column(modifier = Modifier.fillMaxSize()) {
      // Outside the scroll container so it stays put, like the design's sticky header.
      LandingHeader(
        colors = colors,
        type = type,
        showNavLinks = !compact,
        onNavFeatures = { scrollTo(featuresY) },
        onNavHow = { scrollTo(howY) },
        onNavFaq = { scrollTo(faqY) },
        onNavApp = { scrollTo(appY) },
      )
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(scrollState),
      ) {
        Hero(
          modifier = anchor { heroY = it },
          colors = colors,
          type = type,
          metrics = metrics,
          stacked = stacked,
          signingIn = signingIn,
          error = error,
          onGoogle = { signIn(PendingSignIn.Google) },
          onApple = { signIn(PendingSignIn.Apple) },
          onChooseEmail = onChooseEmail,
          onNavApp = { scrollTo(appY) },
        )
        FeaturesSection(anchor { featuresY = it }, colors, type, metrics)
        HowItWorksSection(anchor { howY = it }, colors, type, metrics)
        FaqSection(anchor { faqY = it }, colors, type, metrics, stacked)
        GetTheAppSection(anchor { appY = it }, colors, type, metrics)
        FinalCta(
          colors = colors,
          type = type,
          metrics = metrics,
          onGetStarted = { scrollTo(heroY) },
          onGetApp = { scrollTo(appY) },
        )
        LandingFooter(colors = colors, onNavApp = { scrollTo(appY) })
      }
    }
  }
}

private const val SignInFailed = "Sign-in failed. Please try again."

/** The design's `max-width: 1200px` content column. */
private val ContentMaxWidth = 1200.dp
private val PagePadding = 24.dp

private val CardShape = RoundedCornerShape(12.dp)
private val ButtonShape = RoundedCornerShape(16.dp)

private data class LandingType(val headline: FontFamily, val mono: FontFamily)

/** Sizes that follow the viewport, standing in for the design's `clamp()` values. */
private data class LandingMetrics(
  val heroTitle: TextUnit,
  val sectionTitle: TextUnit,
  val sectionPadding: Dp,
)

/** A full-width band with the content column centred in it. */
@Composable
private fun Band(
  modifier: Modifier = Modifier,
  background: Color,
  verticalPadding: Dp,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(background),
    contentAlignment = Alignment.TopCenter,
  ) {
    Box(
      modifier = Modifier
        .widthIn(max = ContentMaxWidth)
        .fillMaxWidth()
        .padding(horizontal = PagePadding, vertical = verticalPadding),
    ) {
      content()
    }
  }
}

@Composable
private fun HairlineRule(color: Color) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(1.dp)
      .background(color),
  )
}

// ---------------------------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------------------------

@Composable
private fun LandingHeader(
  colors: LandingColors,
  type: LandingType,
  showNavLinks: Boolean,
  onNavFeatures: () -> Unit,
  onNavHow: () -> Unit,
  onNavFaq: () -> Unit,
  onNavApp: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth()
      .background(colors.panel)
  ) {
    Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = Alignment.TopCenter
    ) {
      Row(
        modifier = Modifier
          .widthIn(max = ContentMaxWidth)
          .fillMaxWidth()
          .height(64.dp)
          .padding(horizontal = PagePadding),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Brand(colors, type)
        Spacer(Modifier.weight(1f))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (showNavLinks) {
            NavLink("Features", colors, onNavFeatures)
            NavLink("How it works", colors, onNavHow)
            NavLink("FAQ", colors, onNavFaq)
          }
          GetTheAppLink(colors, onNavApp)
        }
      }
    }
    HairlineRule(colors.outline)
  }
}

/** The brand plane in the brand blue, no tile, then the wordmark with "It" in blue. */
@Composable
private fun Brand(colors: LandingColors, type: LandingType) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = BrandPlane,
      contentDescription = null,
      modifier = Modifier.size(32.dp),
      tint = colors.blue,
    )
    Text(
      text = buildAnnotatedString {
        append("Squawk")
        withStyle(SpanStyle(color = colors.blue)) { append("It") }
      },
      style = TextStyle(
        fontFamily = type.headline,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.2).sp,
        color = colors.heading,
      ),
    )
  }
}

@Composable
private fun NavLink(label: String, colors: LandingColors, onClick: () -> Unit) {
  Text(
    text = label,
    style = TextStyle(
      fontSize = 14.sp,
      fontWeight = FontWeight.Medium,
      color = colors.slate
    ),
    modifier = Modifier
      .clip(CardShape)
      .clickable { onClick() }
      .padding(horizontal = 12.dp, vertical = 8.dp),
  )
}

@Composable
private fun GetTheAppLink(colors: LandingColors, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .clip(CardShape)
      .background(colors.sky)
      .clickable { onClick() }
      .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.Smartphone,
      contentDescription = null,
      modifier = Modifier.size(18.dp),
      tint = colors.onSky,
    )
    Text(
      text = "Get the app",
      style = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = colors.onSky
      ),
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Hero + login card
// ---------------------------------------------------------------------------------------------

@Composable
private fun Hero(
  modifier: Modifier,
  colors: LandingColors,
  type: LandingType,
  metrics: LandingMetrics,
  stacked: Boolean,
  signingIn: PendingSignIn?,
  error: String?,
  onGoogle: () -> Unit,
  onApple: () -> Unit,
  onChooseEmail: () -> Unit,
  onNavApp: () -> Unit,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(colors.navy),
    contentAlignment = Alignment.TopCenter,
  ) {
    Box(
      modifier = Modifier
        .widthIn(max = ContentMaxWidth)
        .fillMaxWidth()
        .padding(horizontal = PagePadding)
        .padding(
          top = if (stacked) 56.dp else 72.dp,
          bottom = if (stacked) 64.dp else 80.dp
        ),
    ) {
      val card: @Composable (Modifier) -> Unit = {
        LoginCard(
          modifier = it,
          colors = colors,
          type = type,
          signingIn = signingIn,
          error = error,
          onGoogle = onGoogle,
          onApple = onApple,
          onChooseEmail = onChooseEmail,
          onNavApp = onNavApp,
        )
      }
      if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(56.dp)) {
          HeroCopy(colors, type, metrics)
          Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            card(
              Modifier.widthIn(max = 460.dp)
                .fillMaxWidth()
            )
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(56.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.weight(1f)) {
            HeroCopy(
              colors,
              type,
              metrics
            )
          }
          Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
          ) {
            card(
              Modifier.widthIn(max = 460.dp)
                .fillMaxWidth()
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HeroCopy(
  colors: LandingColors,
  type: LandingType,
  metrics: LandingMetrics
) {
  Column(
    modifier = Modifier.widthIn(max = 600.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Text(
      text = buildAnnotatedString {
        append("Know what’s due.\nLog what’s done.\n")
        withStyle(SpanStyle(color = colors.heroAccent)) { append("For everything you own.") }
      },
      style = TextStyle(
        fontFamily = type.headline,
        fontWeight = FontWeight.Bold,
        fontSize = metrics.heroTitle,
        lineHeight = metrics.heroTitle * 1.05f,
        letterSpacing = (-1).sp,
        color = Color.White,
      ),
    )
    Text(
      text = "From the annual on your airplane to the oil change on your car and the filter in your furnace — one place for every schedule, every issue, and the people who help.",
      style = TextStyle(
        fontSize = 18.sp,
        lineHeight = 28.sp,
        color = colors.heroBody
      ),
      modifier = Modifier.widthIn(max = 520.dp),
    )
    ThingChips(colors)
  }
}

/** The presets as chips: the fastest way to say "not just airplanes" is to show the others. */
@Composable
private fun ThingChips(colors: LandingColors) {
  val things = listOf(
    "airplane" to "Airplane",
    "automotive" to "Car & motorcycle",
    "bike" to "Bike",
    "boat" to "Boat",
    "home" to "Home",
    "custom" to "And more",
  )
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    things.forEach { (key, label) ->
      Row(
        modifier = Modifier
          .clip(CardShape)
          .background(colors.heroChipBackground)
          .border(1.dp, colors.heroChipBorder, CardShape)
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = thingIcon(key),
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = colors.heroBody,
        )
        Text(
          text = label,
          style = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.heroBody
          ),
        )
      }
    }
  }
}

@Composable
private fun LoginCard(
  modifier: Modifier,
  colors: LandingColors,
  type: LandingType,
  signingIn: PendingSignIn?,
  error: String?,
  onGoogle: () -> Unit,
  onApple: () -> Unit,
  onChooseEmail: () -> Unit,
  onNavApp: () -> Unit,
) {
  Column(
    modifier = modifier
      .clip(CardShape)
      .background(colors.panel)
      .border(1.dp, colors.outline, CardShape)
      .padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = "Log in to SquawkIt",
        style = TextStyle(
          fontFamily = type.headline,
          fontWeight = FontWeight.Bold,
          fontSize = 24.sp,
          lineHeight = 32.sp,
          color = colors.heading,
        ),
      )
      Text(
        text = "Your records stay synced across every device.",
        style = TextStyle(
          fontSize = 14.sp,
          lineHeight = 20.sp,
          color = colors.muted
        ),
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      AuthButton(
        colors = colors,
        enabled = signingIn == null,
        loading = signingIn == PendingSignIn.Google,
        onClick = onGoogle,
        label = "Continue with Google",
        leading = {
          Image(
            imageVector = GoogleLogo,
            contentDescription = null,
            modifier = Modifier.size(AuthButtonIconSize),
          )
        },
      )
      // Offered on every platform, like the shared LoginScreen.
      AuthButton(
        colors = colors,
        enabled = signingIn == null,
        loading = signingIn == PendingSignIn.Apple,
        onClick = onApple,
        label = "Continue with Apple",
        leading = {
          Icon(
            imageVector = AppleLogo,
            contentDescription = null,
            modifier = Modifier.size(AuthButtonIconSize),
            tint = colors.ink,
          )
        },
      )
      // Passwordless email link — navigates to the shared EmailSignInScreen.
      AuthButton(
        colors = colors,
        enabled = signingIn == null,
        loading = false,
        onClick = onChooseEmail,
        label = "Continue with email",
        leading = {
          Icon(
            imageVector = Icons.Outlined.MailOutline,
            contentDescription = null,
            modifier = Modifier.size(AuthButtonIconSize),
            tint = colors.blue,
          )
        },
      )
      if (error != null) {
        Text(
          text = error,
          style = TextStyle(fontSize = 13.sp, color = Color(0xFFBA1A1A))
        )
      }
    }

    if (ShowHeroPromo) HeroPromo(colors, onNavApp)

    Column {
      HairlineRule(colors.buttonOutline.copy(alpha = 0.4f))
      Text(
        text = "SquawkIt is a personal convenience tool, not a certified maintenance record system. It does not replace official logbooks or records required by any authority.",
        style = TextStyle(
          fontSize = 12.sp,
          lineHeight = 18.sp,
          color = colors.muted
        ),
        modifier = Modifier.padding(top = 16.dp),
      )
    }
  }
}

/** The "Now on Android" tile inside the login card; jumps to the Get-the-app section. */
@Composable
private fun HeroPromo(colors: LandingColors, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(CardShape)
      .background(colors.card)
      .border(1.dp, colors.outline, CardShape)
      .clickable { onClick() }
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(colors.sky),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.Smartphone,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = colors.onSky,
      )
    }
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = "Now on Android",
        style = TextStyle(
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          color = colors.heading
        ),
      )
      Text(
        text = "Log work from the hangar or the garage.",
        style = TextStyle(fontSize = 12.sp, color = colors.muted),
      )
    }
    Icon(
      imageVector = Icons.Outlined.ExpandMore,
      contentDescription = null,
      modifier = Modifier.size(20.dp)
        .rotate(-90f),
      tint = colors.muted.copy(alpha = 0.6f),
    )
  }
}

/**
 * The provider mark in an [AuthButton]. A single value because the trailing spacer that balances it
 * has to match: if they drift, the label stops being centred.
 */
private val AuthButtonIconSize = 20.dp

@Composable
private fun AuthButton(
  colors: LandingColors,
  enabled: Boolean,
  loading: Boolean,
  onClick: () -> Unit,
  label: String,
  leading: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(52.dp)
      .clip(ButtonShape)
      .background(colors.panel)
      .border(1.dp, colors.buttonOutline, ButtonShape)
      .clickable(enabled = enabled) { onClick() }
      .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(22.dp),
        strokeWidth = 2.dp,
        color = colors.ink,
      )
    } else {
      // The same layout rule the native sign-in buttons use — icon pinned to the leading edge,
      // label centred in what is left — so the two never drift apart.
      LoginButtonContent(
        label = label,
        labelStyle = TextStyle(
          fontSize = 15.sp,
          fontWeight = FontWeight.Medium,
          color = colors.ink
        ),
        iconSize = AuthButtonIconSize,
        icon = leading,
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Section furniture
// ---------------------------------------------------------------------------------------------

/** Kicker in the mono face, the section title, and an optional lede. Left-aligned, 640 wide. */
@Composable
private fun SectionHeading(
  colors: LandingColors,
  type: LandingType,
  metrics: LandingMetrics,
  kick: String,
  title: String,
  subtitle: String? = null,
  kickColor: Color = colors.blue,
  titleColor: Color = colors.heading,
  subtitleColor: Color = colors.slate,
  maxWidth: Dp = 640.dp,
) {
  Column(
    modifier = Modifier.widthIn(max = maxWidth),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Kicker(kick, type, kickColor)
    SectionTitle(title, type, metrics, titleColor)
    if (subtitle != null) {
      Text(
        text = subtitle,
        style = TextStyle(
          fontSize = 16.sp,
          lineHeight = 24.sp,
          color = subtitleColor
        ),
      )
    }
  }
}

@Composable
private fun Kicker(text: String, type: LandingType, color: Color) {
  Text(
    text = text.uppercase(),
    style = TextStyle(
      fontFamily = type.mono,
      fontWeight = FontWeight.Medium,
      fontSize = 12.sp,
      color = color,
    ),
  )
}

@Composable
private fun SectionTitle(
  text: String,
  type: LandingType,
  metrics: LandingMetrics,
  color: Color,
  textAlign: TextAlign = TextAlign.Start,
) {
  Text(
    text = text,
    style = TextStyle(
      fontFamily = type.headline,
      fontWeight = FontWeight.Bold,
      fontSize = metrics.sectionTitle,
      lineHeight = metrics.sectionTitle * 1.15f,
      letterSpacing = (-0.5).sp,
      color = color,
      textAlign = textAlign,
    ),
  )
}

/**
 * The design's `repeat(auto-fit, minmax(minItemWidth, 1fr))` grid: as many equal columns as fit,
 * every card in a row stretched to the tallest.
 */
@Composable
private fun CardGrid(
  count: Int,
  minItemWidth: Dp,
  gap: Dp,
  item: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val columns = ((maxWidth + gap) / (minItemWidth + gap)).toInt()
      .coerceIn(1, count)
    val rows = (count + columns - 1) / columns
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
      for (row in 0 until rows) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
          horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
          for (column in 0 until columns) {
            val index = row * columns + column
            if (index < count) {
              item(
                index,
                Modifier.weight(1f)
                  .fillMaxHeight()
              )
            } else {
              Spacer(Modifier.weight(1f))
            }
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Features
// ---------------------------------------------------------------------------------------------

private class Feature(
  val icon: ImageVector,
  val title: String,
  val body: String
)

private val Features = listOf(
  Feature(
    Icons.Outlined.Category,
    "Track anything you want",
    "Airplane, car, bike, boat, home — or a custom type. Each brings its own fields, meters and a recommended starter schedule.",
  ),
  Feature(
    Icons.Outlined.EventRepeat,
    "Tasks that recur the right way",
    "By calendar time, by meter — engine hours, odometer, ride distance — or on condition. Whichever comes first.",
  ),
  Feature(
    Icons.Outlined.WarningAmber,
    "An issue log that fits the thing",
    "Squawks on an airplane, issues on a car, attention items at home. Anything that grounds it surfaces first.",
  ),
  Feature(
    Icons.Outlined.NotificationsActive,
    "Due-soon and overdue reminders",
    "Know before anything lapses. Safety-critical items always sit at the top of the list.",
  ),
  Feature(
    Icons.Outlined.Group,
    "Share with the people who help",
    "Invite a mechanic, co-owner or family member. Everyone logs against the same record.",
  ),
  Feature(
    Icons.Outlined.Download,
    "Export your records",
    "PDF or CSV of a thing’s full history, any time. Your data is yours.",
  ),
)

@Composable
private fun FeaturesSection(
  modifier: Modifier,
  colors: LandingColors,
  type: LandingType,
  metrics: LandingMetrics,
) {
  Band(
    modifier = modifier,
    background = colors.surface,
    verticalPadding = metrics.sectionPadding
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(48.dp)) {
      SectionHeading(
        colors = colors,
        type = type,
        metrics = metrics,
        kick = "What it does",
        title = "Everything that keeps your things in service",
        subtitle = "Inspections, recurring tasks, and issues in one place — so nothing slips between services.",
      )
      CardGrid(
        count = Features.size,
        minItemWidth = 300.dp,
        gap = 16.dp
      ) { index, itemModifier ->
        val feature = Features[index]
        Column(
          modifier = itemModifier
            .clip(CardShape)
            .background(colors.card)
            .border(1.dp, colors.outline, CardShape)
            .padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CardShape)
              .background(colors.sky),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = feature.icon,
              contentDescription = null,
              modifier = Modifier.size(24.dp),
              tint = colors.blue,
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = feature.title,
              style = TextStyle(
                fontFamily = type.headline,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                color = colors.heading,
              ),
            )
            Text(
              text = feature.body,
              style = TextStyle(
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = colors.slate
              ),
            )
          }
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// How it works
// ---------------------------------------------------------------------------------------------

private class Step(val title: String, val body: String)

private val Steps = listOf(
  Step(
    "Sign in",
    "Google, Apple or email. No setup — your records are ready wherever you log in."
  ),
  Step(
    "Pick what you maintain",
    "Add a thing, choose its type, and get a starter schedule you can edit."
  ),
  Step(
    "Log as you go — together",
    "Record work and issues from any device, invite the people who help, and get reminders before anything lapses.",
  ),
)

@Composable
private fun HowItWorksSection(
  modifier: Modifier,
  colors: LandingColors,
  type: LandingType,
  metrics: LandingMetrics,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    HairlineRule(colors.outline)
    Band(background = colors.panel, verticalPadding = metrics.sectionPadding) {
      Column(verticalArrangement = Arrangement.spacedBy(48.dp)) {
        SectionHeading(
          colors = colors,
          type = type,
          metrics = metrics,
          kick = "How it works",
          title = "From sign-in to in service in three steps",
        )
        CardGrid(
          count = Steps.size,
          minItemWidth = 280.dp,
          gap = 16.dp
        ) { index, itemModifier ->
          val step = Steps[index]
          Column(
            modifier = itemModifier
              .clip(CardShape)
              .background(colors.panel)
              .border(1.dp, colors.outline, CardShape)
              .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
          ) {
            Text(
              text = (index + 1).toString()
                .padStart(2, '0'),
              style = TextStyle(
                fontFamily = type.mono,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = colors.blue,
              ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = step.title,
                style = TextStyle(
                  fontFamily = type.headline,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 20.sp,
                  lineHeight = 26.sp,
                  color = colors.heading,
                ),
              )
              Text(
                text = step.body,
                style = TextStyle(
                  fontSize = 14.sp,
                  lineHeight = 21.sp,
                  color = colors.slate
                ),
              )
            }
          }
        }
      }
    }
    HairlineRule(colors.outline)
  }
}

// ---------------------------------------------------------------------------------------------
// FAQ
// ---------------------------------------------------------------------------------------------

private val Faqs = listOf(
  "What can I track?" to
    "Aircraft (airframe, engine, propeller), cars and motorcycles, bikes, boats, homes — or anything else you maintain, with a custom type. Each kind of thing comes with its own vocabulary, fields, meters and a recommended starter schedule. Tasks recur by calendar time, by meter — engine hours, odometer, ride distance — or on condition.",
  "Can I share a thing with someone else?" to
    "Yes. Invite a co-owner, mechanic or family member to a specific thing. They see its schedule and history and can log work against it; you stay the owner.",
  "Does SquawkIt work offline?" to
    "The Android app keeps your records on the device and syncs when you reconnect. The web app needs a connection.",
  "How does exporting work?" to
    "Any thing can be exported as a PDF or CSV of its full history from the Overview screen. Exports include every log entry, task and issue.",
  "Is SquawkIt a certified maintenance record system?" to
    "No. SquawkIt is a personal convenience tool. It does not replace official logbooks or records required by your aviation authority or any other regulator.",
  "Which platforms is SquawkIt available on?" to
    "The web app works in any modern browser. The Android app is available on Google Play.",
)

@Composable
private fun FaqSection(
  modifier: Modifier,
  colors: LandingColors,
  type: LandingType,
  metrics: LandingMetrics,
  stacked: Boolean,
) {
  Band(
    modifier = modifier,
    background = colors.surface,
    verticalPadding = metrics.sectionPadding
  ) {
    val uriHandler = LocalUriHandler.current
    val heading: @Composable () -> Unit = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(
          colors = colors,
          type = type,
          metrics = metrics,
          kick = "Questions",
          title = "Frequently asked questions",
        )
        Text(
          text = buildAnnotatedString {
            append("Something else? ")
            withStyle(SpanStyle(color = colors.blue)) { append("Contact support") }
            append(".")
          },
          style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = colors.slate),
          modifier = Modifier.clickable { uriHandler.openUri(SupportUrl) },
        )
      }
    }
    if (stacked) {
      Column(verticalArrangement = Arrangement.spacedBy(48.dp)) {
        heading()
        FaqList(colors, type)
      }
    } else {
      // The design's 1:2 split: heading in the first column, the list spanning the other two.
      Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Box(modifier = Modifier.weight(1f)) { heading() }
        Box(modifier = Modifier.weight(2f)) { FaqList(colors, type) }
      }
    }
  }
}

/** One question open at a time, the first by default. */
@Composable
private fun FaqList(colors: LandingColors, type: LandingType) {
  var openIndex by remember { mutableStateOf(0) }
  Column(modifier = Modifier.fillMaxWidth()) {
    HairlineRule(colors.outline)
    Faqs.forEachIndexed { index, (question, answer) ->
      val open = index == openIndex
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { openIndex = if (open) -1 else index }
            .padding(horizontal = 4.dp, vertical = 20.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Text(
            text = question,
            style = TextStyle(
              fontFamily = type.headline,
              fontWeight = FontWeight.SemiBold,
              fontSize = 17.sp,
              lineHeight = 24.sp,
              color = colors.heading,
            ),
            modifier = Modifier.weight(1f),
          )
          Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(22.dp)
              .rotate(if (open) 180f else 0f),
            tint = colors.blue,
          )
        }
        if (open) {
          Text(
            text = answer,
            style = TextStyle(
              fontSize = 15.sp,
              lineHeight = 23.sp,
              color = colors.slate
            ),
            modifier = Modifier
              .widthIn(max = 680.dp)
              .padding(start = 4.dp, end = 4.dp, bottom = 20.dp),
          )
        }
        HairlineRule(colors.outline)
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Get the app
// ---------------------------------------------------------------------------------------------

@Composable
private fun GetTheAppSection(
  modifier: Modifier,
  colors: LandingColors,
  type: LandingType,
  metrics: LandingMetrics,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    HairlineRule(colors.outline)
    Band(background = colors.panel, verticalPadding = metrics.sectionPadding) {
      BoxWithConstraints {
        // clamp(32px, 5vw, 64px)
        val inset = (maxWidth.value * 0.05f).coerceIn(32f, 64f).dp
        val gap = 48.dp
        val twoColumns = maxWidth - inset * 2 >= 320.dp * 2 + gap
        val copy: @Composable (Modifier) -> Unit =
          { GetTheAppCopy(it, colors, type, metrics) }
        val phone: @Composable () -> Unit = { PhoneMockup(colors, type) }
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.sky)
            .border(1.dp, colors.skyBorder, CardShape)
            .padding(inset),
        ) {
          if (twoColumns) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(gap),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              copy(Modifier.weight(1f))
              Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
              ) { phone() }
            }
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
              copy(Modifier.fillMaxWidth())
              Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
              ) { phone() }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun GetTheAppCopy(
  modifier: Modifier,
  colors: LandingColors,
  type: LandingType,
  metrics: LandingMetrics,
) {
  val uriHandler = LocalUriHandler.current
  Column(
    modifier = modifier.widthIn(max = 520.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    SectionHeading(
      colors = colors,
      type = type,
      metrics = metrics,
      kick = "Mobile app",
      title = "Take SquawkIt to the hangar",
      subtitle = "Log tach time, close out a squawk, or snap a photo of the work order right where it happens. Everything syncs with the web app.",
      kickColor = colors.blueDeep,
      titleColor = colors.onSky,
      subtitleColor = colors.onSkyBody,
      maxWidth = 520.dp,
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      StoreButton(
        colors = colors,
        type = type,
        icon = {
          Icon(
            Icons.Filled.PlayArrow,
            null,
            Modifier.size(24.dp),
            tint = Color.White
          )
        },
        eyebrow = "Get it on",
        name = "Google Play",
        onClick = { uriHandler.openUri(PlayStoreUrl) },
      )
      val appStoreUrl = AppStoreUrl
      if (appStoreUrl != null) {
        StoreButton(
          colors = colors,
          type = type,
          icon = {
            Icon(
              AppleLogo,
              null,
              Modifier.size(22.dp),
              tint = Color.White
            )
          },
          eyebrow = "Download on the",
          name = "App Store",
          onClick = { uriHandler.openUri(appStoreUrl) },
        )
      } else if (ShowAppStoreComingSoon) {
        ComingSoonStore(colors, type, name = "App Store") {
          Icon(AppleLogo, null, Modifier.size(22.dp), tint = colors.slate)
        }
      }
    }
    Text(
      text = "Android 13 or later.",
      style = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = colors.onSkyMuted
      ),
    )
  }
}

/** A store badge: eyebrow over the store name, on navy. */
@Composable
private fun StoreButton(
  colors: LandingColors,
  type: LandingType,
  icon: @Composable () -> Unit,
  eyebrow: String,
  name: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .height(52.dp)
      .clip(ButtonShape)
      .background(colors.navy)
      .clickable { onClick() }
      .padding(start = 14.dp, end = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    icon()
    StoreLabel(
      type,
      eyebrow,
      name,
      eyebrowColor = Color.White.copy(alpha = 0.85f),
      nameColor = Color.White
    )
  }
}

/** The dashed, inert stand-in for a store the app is not on yet. */
@Composable
private fun ComingSoonStore(
  colors: LandingColors,
  type: LandingType,
  name: String,
  icon: @Composable () -> Unit,
) {
  Row(
    modifier = Modifier
      .height(52.dp)
      .drawBehind {
        drawRoundRect(
          color = colors.slate,
          cornerRadius = CornerRadius(16.dp.toPx()),
          style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
              floatArrayOf(
                6.dp.toPx(),
                4.dp.toPx()
              )
            ),
          ),
        )
      }
      .padding(start = 14.dp, end = 18.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    icon()
    StoreLabel(
      type,
      "Coming soon",
      name,
      eyebrowColor = colors.slate,
      nameColor = colors.slate
    )
  }
}

@Composable
private fun StoreLabel(
  type: LandingType,
  eyebrow: String,
  name: String,
  eyebrowColor: Color,
  nameColor: Color,
) {
  Column {
    Text(
      text = eyebrow.uppercase(),
      style = TextStyle(
        fontSize = 10.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
        color = eyebrowColor,
      ),
    )
    Text(
      text = name,
      style = TextStyle(
        fontFamily = type.headline,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 19.sp,
        color = nameColor,
      ),
    )
  }
}

/** The design's phone illustration: a fleet list with one overdue, one due-soon and one clear. */
@Composable
private fun PhoneMockup(colors: LandingColors, type: LandingType) {
  Box(
    modifier = Modifier
      .width(260.dp)
      .aspectRatio(9f / 19f)
      .clip(RoundedCornerShape(32.dp))
      .background(colors.navy)
      .padding(10.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(24.dp))
        .background(colors.panel)
        .padding(start = 14.dp, top = 44.dp, end = 14.dp, bottom = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Fleet",
        style = TextStyle(
          fontFamily = type.headline,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = colors.heading,
        ),
      )
      MockThingCard(
        colors,
        type,
        "N172SP",
        "Cessna 172S · Annual",
        border = Color(0xFFBA1A1A)
      ) {
        MockBadge(
          "OVERDUE",
          background = Color(0xFFFFDAD6),
          color = Color(0xFF410002)
        )
      }
      MockThingCard(
        colors,
        type,
        "4Runner",
        "Oil change · 210 mi",
        border = Color(0xFF8B5E00)
      ) {
        MockBadge(
          "DUE SOON",
          background = Color(0xFFFFECB3),
          color = Color(0xFF5B3D00)
        )
      }
      MockThingCard(
        colors,
        type,
        "Furnace",
        "Filter · 34 days",
        border = colors.outline,
        badge = null
      )
      Spacer(Modifier.weight(1f))
      Box(
        modifier = Modifier
          .align(Alignment.End)
          .size(44.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(colors.blue),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Outlined.Add, null, Modifier.size(22.dp), tint = Color.White)
      }
    }
  }
}

@Composable
private fun MockThingCard(
  colors: LandingColors,
  type: LandingType,
  name: String,
  caption: String,
  border: Color,
  badge: (@Composable () -> Unit)?,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(CardShape)
      .background(colors.card)
      .border(1.dp, border, CardShape)
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = name,
        style = TextStyle(
          fontFamily = type.mono,
          fontWeight = FontWeight.Medium,
          fontSize = 12.sp,
          color = colors.ink,
        ),
      )
      badge?.invoke()
    }
    Text(
      text = caption,
      style = TextStyle(fontSize = 11.sp, color = colors.muted)
    )
  }
}

@Composable
private fun MockBadge(text: String, background: Color, color: Color) {
  Text(
    text = text,
    style = TextStyle(
      fontSize = 9.sp,
      fontWeight = FontWeight.SemiBold,
      color = color
    ),
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(background)
      .padding(horizontal = 6.dp, vertical = 2.dp),
  )
}

// ---------------------------------------------------------------------------------------------
// Final CTA + footer
// ---------------------------------------------------------------------------------------------

@Composable
private fun FinalCta(
  colors: LandingColors,
  type: LandingType,
  metrics: LandingMetrics,
  onGetStarted: () -> Unit,
  onGetApp: () -> Unit,
) {
  Band(background = colors.navy, verticalPadding = metrics.sectionPadding) {
    Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier.widthIn(max = 640.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        SectionTitle(
          text = "Start keeping better records today",
          type = type,
          metrics = metrics,
          color = Color.White,
          textAlign = TextAlign.Center,
        )
        Text(
          text = "Free to start. Sign in, pick what you’re maintaining, and have a schedule in under a minute.",
          style = TextStyle(
            fontSize = 17.sp,
            lineHeight = 26.sp,
            color = colors.heroBody,
            textAlign = TextAlign.Center,
          ),
        )
        FlowRow(
          modifier = Modifier.padding(top = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(
            12.dp,
            Alignment.CenterHorizontally
          ),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .height(52.dp)
              .clip(ButtonShape)
              .background(Color.White)
              .clickable { onGetStarted() }
              .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "Log in to get started",
              style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF001849)
              ),
            )
          }
          Row(
            modifier = Modifier
              .height(52.dp)
              .clip(ButtonShape)
              .border(1.dp, colors.heroAccent, ButtonShape)
              .clickable { onGetApp() }
              .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Outlined.Smartphone,
              null,
              Modifier.size(20.dp),
              tint = Color.White
            )
            Text(
              text = "Get the Android app",
              style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
              ),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LandingFooter(colors: LandingColors, onNavApp: () -> Unit) {
  val uriHandler = LocalUriHandler.current
  Column(modifier = Modifier.fillMaxWidth()) {
    HairlineRule(colors.outline)
    Band(background = colors.panel, verticalPadding = 32.dp) {
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text = "© 2026 SquawkIt. A personal convenience tool — not a certified maintenance record system, and not a replacement for the official aircraft logbooks required by your aviation authority or any other record you are required to keep.",
          style = TextStyle(
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.muted
          ),
          modifier = Modifier.widthIn(max = 760.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
          FooterLink(stringResource(Res.string.privacy_notice), colors) {
            uriHandler.openUri("/privacy.html")
          }
          FooterLink("Support", colors) { uriHandler.openUri(SupportUrl) }
          FooterLink("Android app", colors, onNavApp)
        }
      }
    }
  }
}

@Composable
private fun FooterLink(
  label: String,
  colors: LandingColors,
  onClick: () -> Unit
) {
  Text(
    text = label,
    style = TextStyle(
      fontSize = 13.sp,
      fontWeight = FontWeight.Medium,
      color = colors.blue
    ),
    modifier = Modifier.clickable { onClick() },
  )
}
