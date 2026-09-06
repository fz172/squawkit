package dev.fanfly.wingslog.web

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Design tokens and vector assets for the web-only SquawkIt login / SEO landing page
 * (see [WebLoginLandingScreen]). The light set mirrors the `Login Page.dc.html` design verbatim;
 * the dark set keeps the same roles on the app's dark surfaces. Web-only: the native Android and
 * iOS login experiences are unchanged.
 */
internal data class LandingColors(
  /** Hero and final-CTA background. */
  val navy: Color,
  val blue: Color,
  /** Deeper blue for kickers on a sky container. */
  val blueDeep: Color,
  /** Light-blue container: chips, tiles, the Get-the-app panel. */
  val sky: Color,
  val skyBorder: Color,
  val surface: Color,
  /** Header, cards on `surface`, the login card. */
  val panel: Color,
  /** Tinted card on `surface` or `panel`. */
  val card: Color,
  val outline: Color,
  val buttonOutline: Color,
  val heading: Color,
  val ink: Color,
  val slate: Color,
  val muted: Color,
  /** Text on `sky`. */
  val onSky: Color,
  val onSkyBody: Color,
  val onSkyMuted: Color,
) {
  // The hero and CTA sit on navy in both themes, so their text and chips do not change.
  val heroAccent: Color get() = Color(0xFFA7C8FF)
  val heroBody: Color get() = Color(0xFFD5E3FF)
  val heroChipBorder: Color get() = Color(0xFF004785)
  val heroChipBackground: Color get() = Color(0xFF004785).copy(alpha = 0.35f)
}

internal val LightLandingColors = LandingColors(
  navy = Color(0xFF001849),
  blue = Color(0xFF1A5FAE),
  blueDeep = Color(0xFF004785),
  sky = Color(0xFFD5E3FF),
  skyBorder = Color(0xFFA7C8FF),
  surface = Color(0xFFFAFAFA),
  panel = Color(0xFFFFFFFF),
  card = Color(0xFFF2F2F7),
  outline = Color(0xFFE0E0E5),
  buttonOutline = Color(0xFFC6C6C8),
  heading = Color(0xFF001849),
  ink = Color(0xFF1C1C1E),
  slate = Color(0xFF525E72),
  muted = Color(0xFF636366),
  onSky = Color(0xFF001849),
  onSkyBody = Color(0xFF0E1C2B),
  onSkyMuted = Color(0xFF3A4557),
)

internal val DarkLandingColors = LandingColors(
  navy = Color(0xFF06101F),
  blue = Color(0xFF5B9BE8),
  blueDeep = Color(0xFFA7C8FF),
  sky = Color(0xFF12305E),
  skyBorder = Color(0xFF2C5AA0),
  surface = Color(0xFF0B1120),
  panel = Color(0xFF111A2C),
  card = Color(0xFF172033),
  outline = Color(0xFF26324A),
  buttonOutline = Color(0xFF3A4761),
  heading = Color(0xFFF2F6FF),
  ink = Color(0xFFE7EDF8),
  slate = Color(0xFF9AA7BD),
  muted = Color(0xFF8C97AB),
  onSky = Color(0xFFD5E3FF),
  onSkyBody = Color(0xFFC9D6EE),
  onSkyMuted = Color(0xFF9DB2D6),
)

/** The multi-color Google "G" mark (rendered with Image, not tinted). */
internal val GoogleLogo: ImageVector = ImageVector.Builder(
  defaultWidth = 48.dp,
  defaultHeight = 48.dp,
  viewportWidth = 48f,
  viewportHeight = 48f,
)
  .apply {
    addPath(
      PathParser().parsePathString(
        "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
      )
        .toNodes(),
      fill = SolidColor(Color(0xFFEA4335)),
    )
    addPath(
      PathParser().parsePathString(
        "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
      )
        .toNodes(),
      fill = SolidColor(Color(0xFF4285F4)),
    )
    addPath(
      PathParser().parsePathString(
        "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
      )
        .toNodes(),
      fill = SolidColor(Color(0xFFFBBC05)),
    )
    addPath(
      PathParser().parsePathString(
        "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
      )
        .toNodes(),
      fill = SolidColor(Color(0xFF34A853)),
    )
  }
  .build()

/** The Apple mark (tinted to the button's content color via Icon). */
internal val AppleLogo: ImageVector = ImageVector.Builder(
  defaultWidth = 24.dp,
  defaultHeight = 24.dp,
  viewportWidth = 24f,
  viewportHeight = 24f,
)
  .apply {
    addPath(
      PathParser().parsePathString(
        "M17.05 12.04c-.03-2.8 2.29-4.15 2.39-4.21-1.3-1.9-3.32-2.16-4.04-2.19-1.72-.17-3.36 1.01-4.23 1.01-.87 0-2.21-.99-3.64-.96-1.87.03-3.6 1.09-4.56 2.76-1.95 3.38-.5 8.38 1.4 11.12.93 1.34 2.03 2.85 3.47 2.8 1.39-.06 1.92-.9 3.6-.9 1.68 0 2.16.9 3.64.87 1.5-.03 2.45-1.37 3.37-2.72 1.06-1.56 1.5-3.07 1.52-3.15-.03-.01-2.92-1.12-2.95-4.44zM14.28 3.7c.77-.93 1.29-2.22 1.15-3.5-1.11.04-2.46.74-3.25 1.67-.71.82-1.33 2.14-1.16 3.4 1.24.1 2.5-.63 3.26-1.57z",
      )
        .toNodes(),
      fill = SolidColor(Color.Black),
    )
  }
  .build()
