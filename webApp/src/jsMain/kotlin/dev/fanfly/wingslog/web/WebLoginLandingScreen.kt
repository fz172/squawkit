package dev.fanfly.wingslog.web

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fanfly.wingslog.core.ui.adaptive.compose.layoutTierFor
import dev.fanfly.wingslog.core.ui.adaptive.thingIcon
import dev.fanfly.wingslog.core.ui.brand.BrandPlane
import dev.fanfly.wingslog.core.ui.brand.ThingHero
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import dev.fanfly.wingslog.feature.login.LoginButtonContent
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.privacy_notice
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Which sign-in request is currently awaiting a result, so the pressed button shows a spinner while
 * the others are disabled. Null means idle.
 *
 * This is not the list of login methods the page offers — it is only those that suspend *on this
 * page*. The other two options never reach an in-flight state here:
 *
 * - **Email** does not sign in on this page. Its button navigates away to the shared
 *   `EmailSignInScreen`, which owns its own progress state for both legs of the link flow.
 * - **Anonymous** does not exist on web at all: `AppCapability.isAnonymousLoginSupported` is false
 *   here and `AuthManagerImpl.signInAnonymously` refuses, because web requires a real account.
 *
 * Adding entries for them would create states that can never be set.
 */
private enum class PendingSignIn { Google, Apple }

/**
 * The web-only SquawkIt sign-in / SEO landing page — a full marketing page (header, navy hero with
 * the login card, features, how-it-works, FAQ, final CTA, footer) rendered in Compose to match the
 * approved `SquawkIt Login.html` design. Swapped into [dev.fanfly.wingslog.feature.login.AuthFlow]
 * via its `loginContent` slot by [WebApp], so the shared onboarding tail (name entry + welcome) and
 * the real Firebase auth wiring ([LoginViewModel]) are reused unchanged.
 *
 * Web only: the native Android and iOS [dev.fanfly.wingslog.feature.login.LoginScreen] is untouched.
 *
 * Colors and stroke icons come from [WebLandingAssets]. Responsiveness mirrors the design's own CSS
 * breakpoints via [BoxWithConstraints]; light/dark is driven by the app's [AppearanceController] so
 * the whole app stays consistent after sign-in.
 */
@Composable
internal fun WebLoginLandingScreen(
  onLoginSuccess: () -> Unit,
  onChooseEmail: () -> Unit,
  loginViewModel: LoginViewModel = koinViewModel(),
) {
  // The landing page follows the OS light/dark setting (no in-page theme switcher).
  val isDark = isSystemInDarkTheme()
  val colors = if (isDark) DarkLandingColors else LightLandingColors
  val headline = rememberBrandHeadlineFamily()
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
        if (credential != null) onLoginSuccess() else error =
          "Sign-in failed. Please try again."
      } catch (t: Throwable) {
        error = "Sign-in failed. Please try again."
      } finally {
        signingIn = null
      }
    }
    Unit
  }
  val signInWithGoogle = { signIn(PendingSignIn.Google) }
  val signInWithApple = { signIn(PendingSignIn.Apple) }

  // Section anchors for in-page navigation. Each section reports its top in root coordinates; the
  // scroll container reports its own top. Their difference is the scroll-invariant content offset
  // to animate to (positionInParent() isn't available in this Compose version).
  var contentTopY by remember { mutableStateOf(0f) }
  var heroY by remember { mutableStateOf(0f) }
  var featuresY by remember { mutableStateOf(0f) }
  var howY by remember { mutableStateOf(0f) }
  var faqY by remember { mutableStateOf(0f) }
  var appY by remember { mutableStateOf(0f) }
  val scrollTo = { rawY: Float ->
    scope.launch {
      scrollState.animateScrollTo(
        (rawY - contentTopY).roundToInt()
          .coerceAtLeast(0)
      )
    }
    Unit
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.surface),
  ) {
    val w = maxWidth
    val isCompact = layoutTierFor(w).isCompact
    val heroStacked = w < 920.dp

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .onGloballyPositioned { contentTopY = it.positionInRoot().y },
    ) {
      LandingHeader(
        colors = colors,
        headline = headline,
        // Compact tier (phone): drop the nav links, leaving just the brand.
        showNavLinks = !isCompact,
        onNavFeatures = { scrollTo(featuresY) },
        onNavHow = { scrollTo(howY) },
        onNavFaq = { scrollTo(faqY) },
        onNavApp = { scrollTo(appY) },
      )

      Hero(
        modifier = Modifier.onGloballyPositioned {
          heroY = it.positionInRoot().y
        },
        colors = colors,
        headline = headline,
        stacked = heroStacked,
        signingIn = signingIn,
        error = error,
        onGoogle = signInWithGoogle,
        onApple = signInWithApple,
        onChooseEmail = onChooseEmail,
      )

      FeaturesSection(
        modifier = Modifier.onGloballyPositioned {
          featuresY = it.positionInRoot().y
        },
        colors = colors,
        headline = headline,
        compact = heroStacked,
      )

      HowItWorksSection(
        modifier = Modifier.onGloballyPositioned {
          howY = it.positionInRoot().y
        },
        colors = colors,
        headline = headline,
        compact = heroStacked,
      )

      FaqSection(
        modifier = Modifier.onGloballyPositioned {
          faqY = it.positionInRoot().y
        },
        colors = colors,
        headline = headline,
        compact = heroStacked,
      )

      FinalCta(
        colors = colors,
        headline = headline,
        compact = heroStacked,
        onGetStarted = { scrollTo(heroY) },
        onSeeFeatures = { scrollTo(featuresY) },
      )

      GetTheAppSection(
        modifier = Modifier.onGloballyPositioned {
          appY = it.positionInRoot().y
        },
        colors = colors,
        headline = headline,
        compact = heroStacked,
      )

      LandingFooter(colors = colors)
    }
  }
}

private val ContentMaxWidth = 1152.dp

/** App-icon brand mark: the brand plane vector in the brand blue, no background tile. */
@Composable
private fun BrandMark(size: Dp, colors: LandingColors) {
  Icon(
    imageVector = BrandPlane,
    contentDescription = null,
    modifier = Modifier.size(size),
    tint = colors.blue,
  )
}

/** "SquawkIt" wordmark, "It" in the brand blue, as the store listing sets it. */
@Composable
private fun BrandWordmark(
  colors: LandingColors,
  headline: FontFamily,
  fontSize: Int
) {
  Text(
    text = buildAnnotatedString {
      append("Squawk")
      withStyle(SpanStyle(color = colors.blue)) { append("It") }
    },
    style = TextStyle(
      fontFamily = headline,
      fontWeight = FontWeight.Bold,
      fontSize = fontSize.sp,
      letterSpacing = (-0.4).sp,
      color = colors.heading,
    ),
  )
}

@Composable
private fun LandingHeader(
  colors: LandingColors,
  headline: FontFamily,
  showNavLinks: Boolean,
  onNavFeatures: () -> Unit,
  onNavHow: () -> Unit,
  onNavFaq: () -> Unit,
  onNavApp: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(colors.surface),
    contentAlignment = Alignment.TopCenter,
  ) {
    Row(
      modifier = Modifier
        .widthIn(max = ContentMaxWidth)
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(size = 34.dp, colors = colors)
        Spacer(Modifier.width(11.dp))
        BrandWordmark(colors = colors, headline = headline, fontSize = 21)
      }
      Spacer(Modifier.weight(1f))
      if (showNavLinks) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          NavLink("Features", colors, onNavFeatures)
          Spacer(Modifier.width(28.dp))
          NavLink("How it works", colors, onNavHow)
          Spacer(Modifier.width(28.dp))
          NavLink("FAQ", colors, onNavFaq)
          Spacer(Modifier.width(28.dp))
          NavLink("Get the app", colors, onNavApp)
        }
      }
    }
  }
}

@Composable
private fun NavLink(label: String, colors: LandingColors, onClick: () -> Unit) {
  Text(
    text = label,
    style = TextStyle(
      fontSize = 14.5.sp,
      fontWeight = FontWeight.Medium,
      color = colors.slate
    ),
    modifier = Modifier.clickable { onClick() },
  )
}

@Composable
private fun Hero(
  modifier: Modifier,
  colors: LandingColors,
  headline: FontFamily,
  stacked: Boolean,
  signingIn: PendingSignIn?,
  error: String?,
  onGoogle: () -> Unit,
  onApple: () -> Unit,
  onChooseEmail: () -> Unit,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(colors.navy),
    contentAlignment = Alignment.TopCenter,
  ) {
    // Soft radial glow at the top of the hero (mirrors the design's ::before gradient).
    Canvas(modifier = Modifier.matchParentSize()) {
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            colors.blueBright.copy(alpha = 0.22f),
            Color.Transparent
          ),
          center = Offset(size.width / 2f, size.height * 0.04f),
          radius = size.width * 0.6f,
        ),
        center = Offset(size.width / 2f, size.height * 0.04f),
        radius = size.width * 0.6f,
      )
    }

    Column(
      modifier = Modifier
        .widthIn(max = ContentMaxWidth)
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(top = if (stacked) 44.dp else 64.dp, bottom = 96.dp),
    ) {
      if (stacked) {
        HeroCopy(colors, headline, centered = true)
        Spacer(Modifier.height(40.dp))
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          LoginCard(
            modifier = Modifier.widthIn(max = 460.dp),
            colors = colors,
            headline = headline,
            signingIn = signingIn,
            error = error,
            onGoogle = onGoogle,
            onApple = onApple,
            onChooseEmail = onChooseEmail,
          )
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(56.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.weight(1.05f)) {
            HeroCopy(colors, headline, centered = false)
          }
          Box(modifier = Modifier.weight(0.95f)) {
            LoginCard(
              modifier = Modifier.fillMaxWidth(),
              colors = colors,
              headline = headline,
              signingIn = signingIn,
              error = error,
              onGoogle = onGoogle,
              onApple = onApple,
              onChooseEmail = onChooseEmail,
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
  headline: FontFamily,
  centered: Boolean
) {
  val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
  val textAlign = if (centered) TextAlign.Center else TextAlign.Start
  Column(horizontalAlignment = align) {
    // The one-line announcement of the pivot, ahead of the headline: the returning aviation user
    // reads it as "still for me, and now for more"; the new one reads it as an invitation.
    Spacer(Modifier.height(20.dp))
    Text(
      text = buildAnnotatedString {
        append("Maintenance records for everything you own, ")
        withStyle(SpanStyle(color = colors.skyDim)) { append("simplified") }
      },
      style = TextStyle(
        fontFamily = headline,
        fontWeight = FontWeight.Bold,
        fontSize = if (centered) 36.sp else 52.sp,
        lineHeight = if (centered) 42.sp else 56.sp,
        letterSpacing = (-1).sp,
        color = Color.White,
        textAlign = textAlign,
      ),
    )
    Spacer(Modifier.height(22.dp))
    Text(
      text = "From the annual on your airplane to the oil change on your car and the filter in your furnace — know what's due, log what's done, and share it with the people who help.",
      style = TextStyle(
        fontSize = 18.sp,
        lineHeight = 28.sp,
        color = colors.skyDim,
        textAlign = textAlign,
      ),
      modifier = Modifier.widthIn(max = if (centered) 480.dp else 440.dp),
    )
    Spacer(Modifier.height(24.dp))
    ThingStrip(colors = colors, centered = centered)
  }
}

/**
 * The presets, as chips: the fastest way to say "not just airplanes" is to show the others, and
 * "And more" for whatever is not named. Same icons the app's switcher uses, so the promise and the
 * product match.
 */
@Composable
private fun ThingStrip(colors: LandingColors, centered: Boolean) {
  val things = listOf(
    "airplane" to "Airplane",
    "automotive" to "Car & motorcycle",
    "bike" to "Bike",
    "boat" to "Boat",
    "home" to "Home",
    "custom" to "And more",
  )
  // Two rows of three: six chips do not fit the hero column on one line, and a single chip
  // orphaned onto a second row reads as an afterthought rather than a list.
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(
      8.dp,
      if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    maxItemsInEachRow = 3,
  ) {
    things.forEach { (key, label) ->
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(999.dp))
          .background(Color.White.copy(alpha = 0.07f))
          .border(
            1.dp,
            Color.White.copy(alpha = 0.16f),
            RoundedCornerShape(999.dp)
          )
          .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = thingIcon(key),
          contentDescription = null,
          modifier = Modifier.size(15.dp),
          tint = colors.blueBright,
        )
        Spacer(Modifier.width(7.dp))
        Text(
          text = label,
          style = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.trustText,
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
  headline: FontFamily,
  signingIn: PendingSignIn?,
  error: String?,
  onGoogle: () -> Unit,
  onApple: () -> Unit,
  onChooseEmail: () -> Unit,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(24.dp))
      .background(colors.panel)
      .border(1.dp, colors.outline, RoundedCornerShape(24.dp))
      .padding(horizontal = 32.dp, vertical = 34.dp),
  ) {
    // Same hero as the app's login screen, at card scale. Glyphs fly in from beyond the card and
    // are clipped at its edge, which reads as flying into it.
    ThingHero(
      size = 96.dp,
      tint = colors.blue,
      fanTint = colors.blue.copy(alpha = 0.45f),
      modifier = Modifier.offset(x = (-12).dp, y = (-10).dp),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = "Log in to SquawkIt",
      style = TextStyle(
        fontFamily = headline,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        color = colors.heading
      ),
    )
    Spacer(Modifier.height(8.dp))
    Text(
      text = "Sign in to keep your maintenance records in one place. Everything you track stays synced across every device.",
      style = TextStyle(
        fontSize = 14.5.sp,
        lineHeight = 22.sp,
        color = colors.slate
      ),
    )
    Spacer(Modifier.height(26.dp))

    AuthButton(
      container = Color.White,
      contentColor = Color(0xFF1F1F1F),
      border = colors.outline,
      enabled = signingIn == null,
      loading = signingIn == PendingSignIn.Google,
      onClick = onGoogle,
      label = "Log in with Google",
      leading = {
        Image(
          imageVector = GoogleLogo,
          contentDescription = null,
          modifier = Modifier.size(AuthButtonIconSize)
        )
      },
    )
    // Offered on every platform, like the shared LoginScreen.
    Spacer(Modifier.height(12.dp))
    AuthButton(
      container = Color.Black,
      contentColor = Color.White,
      border = null,
      enabled = signingIn == null,
      loading = signingIn == PendingSignIn.Apple,
      onClick = onApple,
      label = "Log in with Apple",
      leading = {
        Icon(
          imageVector = AppleLogo,
          contentDescription = null,
          modifier = Modifier.size(AuthButtonIconSize),
          tint = Color.White
        )
      },
    )
    Spacer(Modifier.height(12.dp))
    // Passwordless email link — navigates to the shared EmailSignInScreen, leaving the promo page.
    AuthButton(
      container = colors.card,
      contentColor = colors.heading,
      border = colors.outline,
      enabled = signingIn == null,
      loading = false,
      onClick = onChooseEmail,
      label = "Log in with email",
      leading = {
        Icon(
          imageVector = IconMail,
          contentDescription = null,
          modifier = Modifier.size(AuthButtonIconSize),
          tint = colors.heading
        )
      },
    )

    if (error != null) {
      Spacer(Modifier.height(14.dp))
      Text(
        text = error,
        style = TextStyle(fontSize = 13.sp, color = Color(0xFFD64545)),
      )
    }

    Spacer(Modifier.height(18.dp))
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(1.dp)
        .background(colors.card),
    )
    Spacer(Modifier.height(18.dp))
    Text(
      text = "SquawkIt is a personal convenience tool and is not a certified maintenance record system. It does not replace the official logbooks or records required by any applicable authority.",
      style = TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 18.sp,
        color = colors.disclaimer
      ),
    )
  }
}

/**
 * The provider mark in an [AuthButton]. A single value because the trailing spacer that balances it
 * has to match: if they drift, the label stops being centred.
 */
private val AuthButtonIconSize = 19.dp

@Composable
private fun AuthButton(
  container: Color,
  contentColor: Color,
  border: Color?,
  enabled: Boolean,
  loading: Boolean,
  onClick: () -> Unit,
  label: String,
  leading: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(14.dp)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(54.dp)
      .clip(shape)
      .background(container)
      .then(
        if (border != null) Modifier.border(
          1.dp,
          border,
          shape
        ) else Modifier
      )
      .clickable(enabled = enabled) { onClick() }
      .padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(22.dp),
        strokeWidth = 2.dp,
        color = contentColor
      )
    } else {
      // The same layout rule the native sign-in buttons use — icon pinned to the leading edge,
      // label centred in what is left — rather than a second copy of it. Only the metrics differ
      // here (this page's own icon size and label style); the alignment is not this page's to have
      // an opinion about, and having one is what let it drift out of step in the first place.
      LoginButtonContent(
        label = label,
        labelStyle = TextStyle(
          fontSize = 15.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = contentColor
        ),
        iconSize = AuthButtonIconSize,
        icon = leading,
      )
    }
  }
}

@Composable
private fun SectionHeading(
  colors: LandingColors,
  headline: FontFamily,
  kick: String,
  title: String,
  subtitle: String? = null,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 660.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = kick.uppercase(),
      style = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = colors.blue
      ),
    )
    Spacer(Modifier.height(12.dp))
    Text(
      text = title,
      style = TextStyle(
        fontFamily = headline,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
        color = colors.heading,
        textAlign = TextAlign.Center,
      ),
    )
    if (subtitle != null) {
      Spacer(Modifier.height(14.dp))
      Text(
        text = subtitle,
        style = TextStyle(
          fontSize = 17.sp,
          lineHeight = 26.sp,
          color = colors.slate,
          textAlign = TextAlign.Center
        ),
      )
    }
  }
}

/** One entry of a [Carousel]: what the spotlight card shows for it. */
private data class Slide(
  val icon: ImageVector? = null,
  val step: Int? = null,
  val title: String,
  val body: String,
)

/**
 * One slide at a time, advancing on its own every [autoAdvanceMillis] and on demand from the
 * arrows or the dots. Six cards of copy in a grid asked the visitor to read everything at once;
 * one card, moving, asks them to read one thing.
 *
 * Auto-advance pauses while the pointer is over the stage — a slide moving out from under a
 * reader is the one thing a carousel must not do — and restarts its clock on every manual step,
 * so a click never gets a half-second follow-on advance.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Carousel(
  slides: List<Slide>,
  colors: LandingColors,
  headline: FontFamily,
  compact: Boolean,
  cardColor: Color = colors.card,
  autoAdvanceMillis: Long = 5_000,
) {
  var index by remember { mutableStateOf(0) }
  var forward by remember { mutableStateOf(true) }
  var hovered by remember { mutableStateOf(false) }
  // Bumped on every manual step so the auto-advance delay restarts from that moment.
  var clock by remember { mutableStateOf(0) }
  val step = { delta: Int ->
    forward = delta > 0
    index = (index + delta).mod(slides.size)
    clock++
  }

  LaunchedEffect(index, hovered, clock) {
    if (hovered || slides.size < 2) return@LaunchedEffect
    delay(autoAdvanceMillis.milliseconds)
    forward = true
    index = (index + 1) % slides.size
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .widthIn(max = 820.dp)
      .onPointerEvent(PointerEventType.Enter) { hovered = true }
      .onPointerEvent(PointerEventType.Exit) { hovered = false },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 18.dp),
    ) {
      CarouselArrow(colors, previous = true, onClick = { step(-1) })
      Box(modifier = Modifier.weight(1f)) {
        AnimatedContent(
          targetState = index,
          transitionSpec = {
            val sign = if (forward) 1 else -1
            (slideInHorizontally(tween(420)) { sign * it / 3 } + fadeIn(
              tween(
                320
              )
            )) togetherWith
              (slideOutHorizontally(tween(420)) { -sign * it / 3 } + fadeOut(
                tween(220)
              ))
          },
          // Every slide sits in the same frame: a stage that changes height between slides would
          // push the dots and the section below it up and down on every advance.
          modifier = Modifier.animateContentSize(tween(320)),
        ) { i ->
          SpotlightCard(slides[i], colors, headline, compact, cardColor)
        }
      }
      CarouselArrow(colors, previous = false, onClick = { step(1) })
    }
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      slides.indices.forEach { i ->
        val active = i == index
        Box(
          modifier = Modifier
            .height(8.dp)
            .width(if (active) 26.dp else 8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) colors.blue else colors.outline)
            .clickable {
              forward = i > index
              index = i
              clock++
            },
        )
      }
    }
  }
}

@Composable
private fun CarouselArrow(
  colors: LandingColors,
  previous: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(40.dp)
      .clip(RoundedCornerShape(999.dp))
      .background(colors.panel)
      .border(1.dp, colors.outline, RoundedCornerShape(999.dp))
      .clickable { onClick() },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = IconChevronDown,
      contentDescription = if (previous) "Previous" else "Next",
      modifier = Modifier.size(18.dp)
        .rotate(if (previous) 90f else -90f),
      tint = colors.blue,
    )
  }
}

/**
 * The one card on stage. Wide layouts put the icon (or step number) beside the copy so the card
 * fills its frame at a reading width; compact stacks them the way the grid cards did.
 */
@Composable
private fun SpotlightCard(
  slide: Slide,
  colors: LandingColors,
  headline: FontFamily,
  compact: Boolean,
  cardColor: Color,
) {
  val shape = RoundedCornerShape(20.dp)
  val frame = Modifier
    .fillMaxWidth()
    .heightIn(min = if (compact) 300.dp else 172.dp)
    .clip(shape)
    .background(cardColor)
    .border(1.dp, colors.outline, shape)
    .padding(
      horizontal = if (compact) 26.dp else 34.dp,
      vertical = if (compact) 28.dp else 32.dp
    )

  val badge: @Composable () -> Unit = {
    val size = if (compact) 50.dp else 64.dp
    val radius = if (compact) 13.dp else 16.dp
    when {
      slide.step != null -> Box(
        modifier = Modifier.size(size)
          .clip(RoundedCornerShape(radius))
          .background(colors.blue),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = slide.step.toString(),
          style = TextStyle(
            fontFamily = headline,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 18.sp else 24.sp,
            color = Color.White,
          ),
        )
      }

      slide.icon != null -> Box(
        modifier = Modifier.size(size)
          .clip(RoundedCornerShape(radius))
          .background(colors.blue.copy(alpha = 0.10f))
          .border(
            1.dp,
            colors.blue.copy(alpha = 0.22f),
            RoundedCornerShape(radius)
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = slide.icon,
          contentDescription = null,
          modifier = Modifier.size(if (compact) 25.dp else 32.dp),
          tint = colors.blue,
        )
      }
    }
  }
  val copy: @Composable ColumnScope.() -> Unit = {
    Text(
      text = slide.title,
      style = TextStyle(
        fontFamily = headline,
        fontWeight = FontWeight.SemiBold,
        fontSize = if (compact) 20.sp else 24.sp,
        lineHeight = if (compact) 26.sp else 30.sp,
        color = colors.heading,
      ),
    )
    Spacer(Modifier.height(if (compact) 10.dp else 12.dp))
    Text(
      text = slide.body,
      style = TextStyle(
        fontSize = if (compact) 15.sp else 17.sp,
        lineHeight = if (compact) 24.sp else 27.sp,
        color = colors.slate,
      ),
    )
  }

  if (compact) {
    Column(modifier = frame) {
      badge()
      Spacer(Modifier.height(18.dp))
      copy()
    }
  } else {
    // Centred, not top-aligned: slides differ in length, and a short one sitting in the top half
    // of a fixed frame reads as unfinished where a centred one reads as deliberate.
    Row(modifier = frame, verticalAlignment = Alignment.CenterVertically) {
      badge()
      Spacer(Modifier.width(26.dp))
      Column(modifier = Modifier.weight(1f)) { copy() }
    }
  }
}

@Composable
private fun FeaturesSection(
  modifier: Modifier,
  colors: LandingColors,
  headline: FontFamily,
  compact: Boolean,
) {
  Box(
    modifier = modifier.fillMaxWidth()
      .background(colors.surface), contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = ContentMaxWidth)
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = if (compact) 64.dp else 88.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      SectionHeading(
        colors = colors,
        headline = headline,
        kick = "What it does",
        title = "Everything that keeps your things in service",
        subtitle = "One place for the inspections, recurring tasks, and issues that matter — so nothing slips between services, whatever you're maintaining.",
      )
      Spacer(Modifier.height(if (compact) 36.dp else 48.dp))
      Carousel(
        slides = listOf(
          Slide(
            icon = IconLayers,
            title = "Track anything you want",
            body = "Airplane, car or motorcycle, bike, boat, home — or anything else. Each type brings its own vocabulary, fields, meters and parts, so a home never asks for a tail number and an airplane never asks for an odometer. Free to start.",
          ),
          Slide(
            icon = IconOffline,
            title = "Works offline, syncs everywhere",
            body = "Records are written to your device first, so the hangar with no signal and the driveway with no Wi-Fi both work. Sign in and everything syncs across phone, tablet and the web the moment you are back online.",
          ),
          Slide(
            icon = IconInspection,
            title = "Schedules that do the math",
            body = "Recurring tasks by calendar, by meter — engine hours, odometer, ride distance — or on condition. Due-soon and overdue reminders rise to the top of every list, so status is the first thing you see.",
          ),
          Slide(
            icon = IconSquawk,
            title = "An issue log that fits the thing",
            body = "Squawks on an airplane, issues on a car, attention items at home. Report it the moment you spot it and track it to resolution — anything that grounds the airplane or parks the car surfaces first.",
          ),
          Slide(
            icon = IconPeople,
            title = "Built for more than one person",
            body = "Invite co-owners, family, or your mechanic with a code. Owners edit, technicians sign off their own work, viewers read — and every change syncs to everyone on the share.",
          ),
          Slide(
            icon = IconListChecks,
            title = "Start with a real schedule",
            body = "Each type ships a recommended starter pack — the annual and ELT check, oil changes and brake fluid, gutters and the water-heater flush. Keep what applies, skip the rest, edit anything later.",
          ),
          Slide(
            icon = IconPaperclip,
            title = "The paperwork travels with the record",
            body = "Photos, invoices and inspection reports attach to the entry they belong to. Export PDF, CSV and XLSX on demand — for a pre-buy, a resale, or a backup that's yours to keep.",
          ),
        ),
        colors = colors,
        headline = headline,
        compact = compact,
      )
    }
  }
}

@Composable
private fun HowItWorksSection(
  modifier: Modifier,
  colors: LandingColors,
  headline: FontFamily,
  compact: Boolean,
) {
  Box(
    modifier = modifier.fillMaxWidth()
      .background(colors.card), contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = ContentMaxWidth)
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = if (compact) 64.dp else 88.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      SectionHeading(
        colors = colors,
        headline = headline,
        kick = "How it works",
        title = "From sign-in to in service in three steps",
      )
      Spacer(Modifier.height(if (compact) 36.dp else 48.dp))
      Carousel(
        slides = listOf(
          Slide(
            step = 1,
            title = "Pick what you're maintaining",
            body = "Sign in with Google or Apple, choose airplane, car, bike, boat, home or custom, and fill in the details that type asks for — no more, no less.",
          ),
          Slide(
            step = 2,
            title = "Accept a starter schedule",
            body = "Keep the recommended tasks that apply, add your own intervals, and let SquawkIt do the date and meter math from then on.",
          ),
          Slide(
            step = 3,
            title = "Log as you go — together",
            body = "Record work and issues from any device, invite the people who help, and get due-soon and overdue reminders before anything lapses.",
          ),
        ),
        colors = colors,
        headline = headline,
        compact = compact,
        // On the `card` surface, so the stage card takes the panel colour to stand off it.
        cardColor = colors.panel,
        // Three steps read slower than six features; give each one longer on stage.
        autoAdvanceMillis = 6_500,
      )
    }
  }
}

@Composable
private fun FaqSection(
  modifier: Modifier,
  colors: LandingColors,
  headline: FontFamily,
  compact: Boolean,
) {
  Box(
    modifier = modifier.fillMaxWidth()
      .background(colors.surface), contentAlignment = Alignment.TopCenter
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = ContentMaxWidth)
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = if (compact) 64.dp else 88.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      SectionHeading(
        colors = colors,
        headline = headline,
        kick = "Questions",
        title = "Frequently asked questions",
      )
      Spacer(Modifier.height(if (compact) 40.dp else 52.dp))
      Column(
        modifier = Modifier.widthIn(max = 780.dp)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        FaqRow(
          colors, headline, initiallyOpen = true,
          question = "What can I track?",
          answer = "Aircraft (airframe, engine, propeller), cars and motorcycles, bikes, boats, homes — or anything else you maintain, with a custom type. Each kind of thing comes with its own vocabulary, fields, meters and a recommended starter schedule. Tasks recur by calendar time, by meter — engine hours, odometer, ride distance — or on condition.",
        )
        FaqRow(
          colors, headline, initiallyOpen = false,
          question = "Can I share a thing with someone else?",
          answer = "Yes. Invite a co-owner, a family member or your mechanic with a code. Roles decide who can edit, who signs off their own work, and who only reads. Shared records sync to everyone, and the host can revoke access at any time.",
        )
        FaqRow(
          colors, headline, initiallyOpen = false,
          question = "Does SquawkIt work offline?",
          answer = "Yes. SquawkIt is local-first, so your records are always available — in the hangar, the garage, or the marina, with no signal at all. Changes sync automatically in the background once you reconnect.",
        )
        FaqRow(
          colors, headline, initiallyOpen = false,
          question = "How does exporting work?",
          answer = "Export any thing's records on demand — pick a date range and SquawkIt generates a PDF, CSV and XLSX bundle covering inspections, tasks, issues and completed work, with current readings and due dates. Download it, or email a copy to a mechanic, a buyer, or your A&P for a pre-buy or an annual. Your certified records stay the source of truth; the export is a convenient, up-to-date snapshot.",
        )
        FaqRow(
          colors, headline, initiallyOpen = false,
          question = "Is SquawkIt a certified maintenance record system?",
          answer = "No. SquawkIt is a personal convenience tool. It does not replace the official aircraft logbooks required by your aviation authority, or any other record you are required to keep. Think of it as the heads-up layer that helps you stay ahead of them.",
        )
        FaqRow(
          colors, headline, initiallyOpen = false,
          question = "Which platforms is SquawkIt available on?",
          answer = "SquawkIt runs on the web, iOS and Android, and a subscription bought on one works on all of them. Everything you track syncs across every device you sign in on.",
        )
      }
    }
  }
}

@Composable
private fun FaqRow(
  colors: LandingColors,
  headline: FontFamily,
  initiallyOpen: Boolean,
  question: String,
  answer: String,
) {
  var open by remember { mutableStateOf(initiallyOpen) }
  val shape = RoundedCornerShape(14.dp)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(colors.panel)
      .border(1.dp, if (open) colors.blue else colors.outline, shape)
      .clickable { open = !open }
      .padding(horizontal = 22.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth()
        .padding(vertical = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = question,
        style = TextStyle(
          fontFamily = headline,
          fontWeight = FontWeight.SemiBold,
          fontSize = 17.sp,
          color = colors.heading
        ),
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(16.dp))
      Icon(
        imageVector = IconChevronDown,
        contentDescription = null,
        modifier = Modifier.size(20.dp)
          .rotate(if (open) 180f else 0f),
        tint = colors.blue,
      )
    }
    if (open) {
      Text(
        text = answer,
        style = TextStyle(
          fontSize = 15.sp,
          lineHeight = 25.sp,
          color = colors.slate
        ),
        modifier = Modifier.padding(bottom = 20.dp),
      )
    }
  }
}

@Composable
private fun FinalCta(
  colors: LandingColors,
  headline: FontFamily,
  compact: Boolean,
  onGetStarted: () -> Unit,
  onSeeFeatures: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxWidth()
      .background(colors.navy),
    contentAlignment = Alignment.TopCenter,
  ) {
    Canvas(modifier = Modifier.matchParentSize()) {
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            colors.blueBright.copy(alpha = 0.3f),
            Color.Transparent
          ),
          center = Offset(size.width / 2f, size.height * 1.1f),
          radius = size.width * 0.5f,
        ),
        center = Offset(size.width / 2f, size.height * 1.1f),
        radius = size.width * 0.5f,
      )
    }
    Column(
      modifier = Modifier
        .widthIn(max = ContentMaxWidth)
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = if (compact) 72.dp else 96.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "Start keeping better records today",
        style = TextStyle(
          fontFamily = headline,
          fontWeight = FontWeight.Bold,
          fontSize = if (compact) 30.sp else 40.sp,
          lineHeight = if (compact) 36.sp else 46.sp,
          letterSpacing = (-0.6).sp,
          color = Color.White,
          textAlign = TextAlign.Center,
        ),
        modifier = Modifier.widthIn(max = 460.dp),
      )
      Spacer(Modifier.height(18.dp))
      Text(
        text = "Free to start. Sign in, pick what you're maintaining, and have a schedule in under a minute.",
        style = TextStyle(
          fontSize = 18.sp,
          lineHeight = 27.sp,
          color = colors.skyDim,
          textAlign = TextAlign.Center
        ),
        modifier = Modifier.widthIn(max = 420.dp),
      )
      Spacer(Modifier.height(32.dp))
      if (compact) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          PillButton(
            "Log in to get started",
            primary = true,
            colors = colors,
            fillWidth = true,
            onClick = onGetStarted
          )
          PillButton(
            "See features",
            primary = false,
            colors = colors,
            fillWidth = true,
            onClick = onSeeFeatures
          )
        }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          PillButton(
            "Log in to get started",
            primary = true,
            colors = colors,
            fillWidth = false,
            onClick = onGetStarted
          )
          PillButton(
            "See features",
            primary = false,
            colors = colors,
            fillWidth = false,
            onClick = onSeeFeatures
          )
        }
      }
    }
  }
}

@Composable
private fun PillButton(
  label: String,
  primary: Boolean,
  colors: LandingColors,
  fillWidth: Boolean,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(14.dp)
  val base = if (fillWidth) Modifier.fillMaxWidth() else Modifier
  Box(
    modifier = base
      .height(54.dp)
      .clip(shape)
      .then(
        if (primary) Modifier.background(Color.White) else Modifier.border(
          1.dp,
          Color.White.copy(
            alpha = 0.28f
          ),
          shape
        )
      )
      .clickable { onClick() }
      .padding(horizontal = 26.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = TextStyle(
        fontSize = 15.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (primary) colors.navy else Color.White,
      ),
    )
  }
}

private const val PlayStoreUrl = "https://play.google.com/store/apps/details?id=dev.fanfly.wingslog"

/**
 * Store links for the mobile apps. Google Play is live; the App Store card is a placeholder until
 * the iOS build is approved, so it stays unclickable rather than linking to a page that 404s.
 */
@Composable
private fun GetTheAppSection(
  modifier: Modifier,
  colors: LandingColors,
  headline: FontFamily,
  compact: Boolean,
) {
  val uriHandler = LocalUriHandler.current
  Box(
    modifier = modifier.fillMaxWidth()
      .background(colors.surface),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = ContentMaxWidth)
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = if (compact) 64.dp else 88.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      SectionHeading(
        colors = colors,
        headline = headline,
        kick = "Mobile apps",
        title = "Take SquawkIt to the hangar",
        subtitle = "The same records on your phone and tablet, offline, and in sync with the web.",
      )
      Spacer(Modifier.height(if (compact) 36.dp else 44.dp))
      val play: @Composable (Modifier) -> Unit = {
        StoreCard(
          modifier = it,
          colors = colors,
          headline = headline,
          icon = GooglePlayLogo,
          tint = null,
          eyebrow = "Get it on",
          name = "Google Play",
          caption = "Available now for Android phones and tablets.",
          onClick = { uriHandler.openUri(PlayStoreUrl) },
        )
      }
      val appStore: @Composable (Modifier) -> Unit = {
        StoreCard(
          modifier = it,
          colors = colors,
          headline = headline,
          icon = AppleLogo,
          tint = colors.heading,
          eyebrow = "Coming soon",
          name = "App Store",
          caption = "The iPhone and iPad app is on its way.",
          onClick = null,
        )
      }
      if (compact) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          play(Modifier.fillMaxWidth())
          appStore(Modifier.fillMaxWidth())
        }
      } else {
        // Intrinsic height so both cards match the taller one's copy.
        Row(
          modifier = Modifier.widthIn(max = 780.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
          horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
          play(Modifier.weight(1f).fillMaxHeight())
          appStore(Modifier.weight(1f).fillMaxHeight())
        }
      }
    }
  }
}

/** One store entry. A null [onClick] renders it as an inert, dimmed placeholder. */
@Composable
private fun StoreCard(
  modifier: Modifier,
  colors: LandingColors,
  headline: FontFamily,
  icon: ImageVector,
  tint: Color?,
  eyebrow: String,
  name: String,
  caption: String,
  onClick: (() -> Unit)?,
) {
  val shape = RoundedCornerShape(20.dp)
  val enabled = onClick != null
  Row(
    modifier = modifier
      .clip(shape)
      .background(colors.card)
      .border(1.dp, colors.outline, shape)
      .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
      .padding(horizontal = 24.dp, vertical = 22.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Box(
      modifier = Modifier.size(56.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(colors.panel)
        .border(1.dp, colors.outline, RoundedCornerShape(14.dp)),
      contentAlignment = Alignment.Center,
    ) {
      if (tint == null) {
        Image(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
      } else {
        Icon(
          imageVector = icon,
          contentDescription = null,
          modifier = Modifier.size(28.dp),
          tint = if (enabled) tint else tint.copy(alpha = 0.55f),
        )
      }
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = eyebrow.uppercase(),
        style = TextStyle(
          fontSize = 11.5.sp,
          fontWeight = FontWeight.SemiBold,
          letterSpacing = 1.sp,
          color = if (enabled) colors.blue else colors.amber,
        ),
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = name,
        style = TextStyle(
          fontFamily = headline,
          fontWeight = FontWeight.Bold,
          fontSize = 21.sp,
          lineHeight = 26.sp,
          letterSpacing = (-0.3).sp,
          color = if (enabled) colors.heading else colors.heading.copy(alpha = 0.7f),
        ),
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = caption,
        style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = colors.slate),
      )
    }
    if (enabled) {
      Icon(
        imageVector = IconChevronDown,
        contentDescription = null,
        modifier = Modifier.size(20.dp)
          .rotate(-90f),
        tint = colors.blue,
      )
    }
  }
}

@Composable
private fun LandingFooter(colors: LandingColors) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(colors.panel),
  ) {
    Box(
      modifier = Modifier.fillMaxWidth()
        .height(1.dp)
        .background(colors.outline)
    )
    Box(
      modifier = Modifier.fillMaxWidth(),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = ContentMaxWidth)
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 40.dp),
      ) {
        Text(
          text = "© 2026 SquawkIt. A personal convenience tool — not a certified maintenance record system, and not a replacement for the official aircraft logbooks required by your aviation authority or any other record you are required to keep. Maintenance records for aircraft, cars, bikes, boats and homes.",
          style = TextStyle(
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = colors.footerCopy
          ),
        )
        Spacer(Modifier.height(14.dp))
        val uriHandler = LocalUriHandler.current
        Text(
          text = stringResource(Res.string.privacy_notice),
          style = TextStyle(
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = colors.blue,
            textDecoration = TextDecoration.Underline,
          ),
          modifier = Modifier.clickable { uriHandler.openUri("/privacy.html") },
        )
      }
    }
  }
}
