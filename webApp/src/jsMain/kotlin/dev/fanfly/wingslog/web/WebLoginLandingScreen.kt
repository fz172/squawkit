package dev.fanfly.wingslog.web

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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.fanfly.wingslog.core.ui.theme.rememberBrandHeadlineFamily
import dev.fanfly.wingslog.feature.login.data.LoginViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wingslog.feature.login.generated.resources.Res
import wingslog.feature.login.generated.resources.privacy_notice
import kotlin.math.roundToInt

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

  var isSigningIn by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  // Returning, already-authenticated users skip straight through (mirrors LoginScreen).
  LaunchedEffect(Unit) {
    val credential = loginViewModel.silentLogin()
    if (credential != null) onLoginSuccess()
  }

  val signInWithGoogle = {
    scope.launch {
      isSigningIn = true
      error = null
      try {
        val credential = loginViewModel.login()
        if (credential != null) onLoginSuccess() else error =
          "Sign-in failed. Please try again."
      } catch (t: Throwable) {
        error = "Sign-in failed. Please try again."
      } finally {
        isSigningIn = false
      }
    }
    Unit
  }

  // Section anchors for in-page navigation. Each section reports its top in root coordinates; the
  // scroll container reports its own top. Their difference is the scroll-invariant content offset
  // to animate to (positionInParent() isn't available in this Compose version).
  var contentTopY by remember { mutableStateOf(0f) }
  var heroY by remember { mutableStateOf(0f) }
  var featuresY by remember { mutableStateOf(0f) }
  var howY by remember { mutableStateOf(0f) }
  var faqY by remember { mutableStateOf(0f) }
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
    val gridColumns = when {
      w < 680.dp -> 1
      w < 1080.dp -> 2
      else -> 3
    }

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
      )

      Hero(
        modifier = Modifier.onGloballyPositioned {
          heroY = it.positionInRoot().y
        },
        colors = colors,
        headline = headline,
        stacked = heroStacked,
        isSigningIn = isSigningIn,
        error = error,
        onGoogle = signInWithGoogle,
        onChooseEmail = onChooseEmail,
      )

      FeaturesSection(
        modifier = Modifier.onGloballyPositioned {
          featuresY = it.positionInRoot().y
        },
        colors = colors,
        headline = headline,
        columns = gridColumns,
        compact = heroStacked,
      )

      HowItWorksSection(
        modifier = Modifier.onGloballyPositioned {
          howY = it.positionInRoot().y
        },
        colors = colors,
        headline = headline,
        columns = gridColumns,
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

/** "SquawkIt" wordmark with the blue trailing dot. */
@Composable
private fun BrandWordmark(
  colors: LandingColors,
  headline: FontFamily,
  fontSize: Int
) {
  Text(
    text = buildAnnotatedString {
      append("SquawkIt")
      withStyle(SpanStyle(color = colors.blue)) { append(".") }
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
  isSigningIn: Boolean,
  error: String?,
  onGoogle: () -> Unit,
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
            isSigningIn = isSigningIn,
            error = error,
            onGoogle = onGoogle,
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
              isSigningIn = isSigningIn,
              error = error,
              onGoogle = onGoogle,
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
    Text(
      text = buildAnnotatedString {
        append("Aircraft maintenance logbook, ")
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
      text = "Track inspections, service bulletins, squawks across your whole fleet, and share reports with others.",
      style = TextStyle(
        fontSize = 18.sp,
        lineHeight = 28.sp,
        color = colors.skyDim,
        textAlign = textAlign,
      ),
      modifier = Modifier.widthIn(max = if (centered) 460.dp else 380.dp),
    )
    Spacer(Modifier.height(28.dp))
    val trust = listOf(
      "Due-soon & overdue reminders",
      "Works offline, syncs everywhere",
      "Export reports on-demand",
      "Free to start",
    )
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      // Items are left-aligned so the checkmarks share an x; the block as a whole is still centered
      // (in compact) by the parent column, since this column wraps to its content width.
      horizontalAlignment = Alignment.Start,
    ) {
      if (centered) {
        trust.forEach { TrustItem(it, colors) }
      } else {
        // Two fixed columns (column-major, like the design's `repeat(2, max-content)`) so the
        // checkmarks line up vertically within each column instead of drifting with text width.
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrustItem(trust[0], colors)
            TrustItem(trust[2], colors)
          }
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TrustItem(trust[1], colors)
            TrustItem(trust[3], colors)
          }
        }
      }
    }
    Spacer(Modifier.height(30.dp))
    Text(
      text = "Built for everything from a non-powered glider to multiple engine fleets",
      style = TextStyle(
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = colors.skyDim,
        textAlign = textAlign
      ),
    )
  }
}

@Composable
private fun TrustItem(label: String, colors: LandingColors) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      imageVector = IconCheck,
      contentDescription = null,
      modifier = Modifier.size(18.dp),
      tint = colors.blueBright,
    )
    Spacer(Modifier.width(9.dp))
    Text(
      text = label,
      style = TextStyle(fontSize = 14.5.sp, color = colors.trustText),
    )
  }
}

@Composable
private fun LoginCard(
  modifier: Modifier,
  colors: LandingColors,
  headline: FontFamily,
  isSigningIn: Boolean,
  error: String?,
  onGoogle: () -> Unit,
  onChooseEmail: () -> Unit,
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(24.dp))
      .background(colors.panel)
      .border(1.dp, colors.outline, RoundedCornerShape(24.dp))
      .padding(horizontal = 32.dp, vertical = 34.dp),
  ) {
    BrandMark(size = 54.dp, colors = colors)
    Spacer(Modifier.height(18.dp))
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
      text = "Log in to manage your aircraft maintenance records. Your fleet stays synced across every device.",
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
      enabled = !isSigningIn,
      loading = isSigningIn,
      onClick = onGoogle,
      label = "Log in with Google",
      leading = {
        Image(
          imageVector = GoogleLogo,
          contentDescription = null,
          modifier = Modifier.size(19.dp)
        )
      },
    )
    Spacer(Modifier.height(12.dp))
    AuthButton(
      container = Color.Black,
      contentColor = Color.White,
      border = null,
      enabled = !isSigningIn,
      loading = false,
      // Apple sign-in is shown on every platform but not yet wired (matches LoginScreen).
      onClick = { /* TODO(apple-signin): wire AuthManager.signInWithApple() for web. */ },
      label = "Log in with Apple",
      leading = {
        Icon(
          imageVector = AppleLogo,
          contentDescription = null,
          modifier = Modifier.size(19.dp),
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
      enabled = !isSigningIn,
      loading = false,
      onClick = onChooseEmail,
      label = "Log in with email",
      leading = {
        Icon(
          imageVector = IconMail,
          contentDescription = null,
          modifier = Modifier.size(19.dp),
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
      text = "SquawkIt is a personal convenience tool and is not a certified maintenance record system. It does not replace the official aircraft logbooks required by your aviation authority.",
      style = TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 18.sp,
        color = colors.disclaimer
      ),
    )
  }
}

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
      .clickable(enabled = enabled) { onClick() },
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
      leading()
      Spacer(Modifier.width(11.dp))
      Text(
        text = label,
        style = TextStyle(
          fontSize = 15.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = contentColor
        ),
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

/** Lays [cells] out in rows of [columns], each cell taking equal width and matched height. */
@Composable
private fun CardGrid(columns: Int, cells: List<@Composable () -> Unit>) {
  Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
    cells.chunked(columns)
      .forEach { rowCells ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
          horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
          rowCells.forEach { cell ->
            Box(
              modifier = Modifier.weight(1f)
                .fillMaxHeight()
            ) { cell() }
          }
          repeat(columns - rowCells.size) { Spacer(Modifier.weight(1f)) }
        }
      }
  }
}

@Composable
private fun FeaturesSection(
  modifier: Modifier,
  colors: LandingColors,
  headline: FontFamily,
  columns: Int,
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
        title = "Everything that keeps your airplane airworthy",
        subtitle = "One place for the inspections, directives, and defects that matter — so nothing slips between annuals.",
      )
      Spacer(Modifier.height(if (compact) 40.dp else 52.dp))
      CardGrid(
        columns = columns,
        cells = listOf(
          {
            FeatureCard(
              colors, headline, IconInspection,
              "Inspection & SB tracking",
              "Annuals, 100-hours, transponder & ELT checks, and service bulletins — time-based, engine-hour, or on-condition. SquawkIt counts down to every due date.",
            )
          },
          {
            FeatureCard(
              colors, headline, IconSquawk,
              "Squawk log",
              "Report defects the moment you spot them and track each one to resolution. Grounding and AOG squawks are surfaced first, so no-go items never get buried.",
            )
          },
          {
            FeatureCard(
              colors, headline, IconMonitor,
              "On the web today",
              "Use SquawkIt in any browser — local-first, so your records open instantly in the hangar and sync in the background once you're back online. Native iOS and Android apps are coming soon.",
            )
          },
        ),
      )
    }
  }
}

@Composable
private fun FeatureCard(
  colors: LandingColors,
  headline: FontFamily,
  icon: ImageVector,
  title: String,
  body: String,
) {
  Column(
    modifier = Modifier
      .fillMaxHeight()
      .clip(RoundedCornerShape(18.dp))
      .background(colors.card)
      .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
      .padding(horizontal = 26.dp, vertical = 30.dp),
  ) {
    Box(
      modifier = Modifier
        .size(50.dp)
        .clip(RoundedCornerShape(13.dp))
        .background(colors.panel)
        .border(1.dp, colors.outline, RoundedCornerShape(13.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(25.dp),
        tint = colors.blue
      )
    }
    Spacer(Modifier.height(20.dp))
    Text(
      text = title,
      style = TextStyle(
        fontFamily = headline,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        color = colors.heading
      ),
    )
    Spacer(Modifier.height(11.dp))
    Text(
      text = body,
      style = TextStyle(
        fontSize = 15.sp,
        lineHeight = 24.sp,
        color = colors.slate
      ),
    )
  }
}

@Composable
private fun HowItWorksSection(
  modifier: Modifier,
  colors: LandingColors,
  headline: FontFamily,
  columns: Int,
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
        title = "From sign-in to airworthy in three steps",
      )
      Spacer(Modifier.height(if (compact) 40.dp else 52.dp))
      CardGrid(
        columns = columns,
        cells = listOf(
          {
            StepCard(
              colors, headline, 1, "Add your aircraft",
              "Sign in with Google or Apple and enter a tail number, make, and model. Track one airplane or a whole fleet.",
            )
          },
          {
            StepCard(
              colors, headline, 2, "Set up your schedule",
              "Add inspections, SBs, and recurring tasks with their intervals. SquawkIt does the date and engine-hour math for you.",
            )
          },
          {
            StepCard(
              colors, headline, 3, "Log as you fly",
              "Record squawks and maintenance work, and get due-soon and overdue reminders before anything lapses.",
            )
          },
        ),
      )
    }
  }
}

@Composable
private fun StepCard(
  colors: LandingColors,
  headline: FontFamily,
  number: Int,
  title: String,
  body: String,
) {
  Column(
    modifier = Modifier
      .fillMaxHeight()
      .clip(RoundedCornerShape(18.dp))
      .background(colors.panel)
      .border(1.dp, colors.outline, RoundedCornerShape(18.dp))
      .padding(horizontal = 26.dp, vertical = 30.dp),
  ) {
    Box(
      modifier = Modifier
        .size(34.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(colors.blue),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = number.toString(),
        style = TextStyle(
          fontFamily = headline,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = Color.White
        ),
      )
    }
    Spacer(Modifier.height(18.dp))
    Text(
      text = title,
      style = TextStyle(
        fontFamily = headline,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = colors.heading
      ),
    )
    Spacer(Modifier.height(10.dp))
    Text(
      text = body,
      style = TextStyle(
        fontSize = 14.5.sp,
        lineHeight = 23.sp,
        color = colors.slate
      ),
    )
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
          question = "Is SquawkIt a certified maintenance record system?",
          answer = "No. SquawkIt is a personal convenience tool. It does not replace the official aircraft logbooks required by your aviation authority — keep your certified records as you always have. Think of it as the heads-up layer that helps you stay ahead of them.",
        )
        FaqRow(
          colors, headline, initiallyOpen = false,
          question = "Does SquawkIt work offline?",
          answer = "Yes. SquawkIt is local-first, so your records are always available — even with no signal in the hangar. Changes sync automatically in the background once you reconnect.",
        )
        FaqRow(
          colors, headline, initiallyOpen = false,
          question = "How does exporting reports work?",
          answer = "Export any aircraft's records on demand — pick a date range and SquawkIt generates a clean PDF report covering inspections, service bulletins, squawks, and completed work, with current hours and due dates. Download it or share the link with a mechanic, buyer, or your A&P/IA for a pre-buy or annual. Your certified logbooks stay the source of truth; the export is a convenient, up-to-date snapshot.",
        )
        FaqRow(
          colors, headline, initiallyOpen = false,
          question = "What aircraft and maintenance items can I track?",
          answer = "Track a single airplane or a whole fleet — airframe, engine, and propeller. SquawkIt supports time-based, engine-hour, and on-condition inspections, service bulletins, and recurring tasks.",
        )
        FaqRow(
          colors, headline, initiallyOpen = false,
          question = "Which platforms is SquawkIt available on?",
          answer = "SquawkIt runs on the web today, with native iOS and Android apps coming soon. Your fleet syncs across every device.",
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
        text = "Start your aircraft logbook today",
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
        text = "Free to start. Sign in and add your first aircraft in under a minute.",
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
          text = "© 2026 SquawkIt. A personal convenience tool — not a certified maintenance record system, and not a replacement for the official aircraft logbooks required by your aviation authority. Aircraft maintenance logbook app for pilots, owners, and mechanics.",
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
