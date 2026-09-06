package dev.fanfly.wingslog.web

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Design tokens and vector assets for the web-only SquawkIt login / SEO landing page
 * (see [WebLoginLandingScreen]). These mirror the `:root` / `[data-theme="dark"]` CSS custom
 * properties and inline SVGs of the source `SquawkIt Login.html` so the Compose render matches the
 * approved design. Web-only: the native Android and iOS login experiences are unchanged.
 */
internal data class LandingColors(
  val navy: Color,
  val navy2: Color,
  val blue: Color,
  val blueBright: Color,
  val sky: Color,
  val skyDim: Color,
  val slate: Color,
  val surface: Color,
  val card: Color,
  val outline: Color,
  val amber: Color,
  val green: Color,
  val ink: Color,
  val panel: Color,
  val heading: Color,
) {
  // Fixed across themes (white text + muted captions on the navy/card surfaces).
  val onNavy: Color get() = Color.White
  val trustText: Color get() = Color(0xFFCDD9F0)
  val disclaimer: Color get() = Color(0xFF8A93A3)
  val footerCopy: Color get() = Color(0xFF9AA3B2)
}

internal val LightLandingColors = LandingColors(
  navy = Color(0xFF001849),
  navy2 = Color(0xFF04205C),
  blue = Color(0xFF1A5FAE),
  blueBright = Color(0xFF3B82E0),
  sky = Color(0xFFD5E3FF),
  skyDim = Color(0xFFA9C2EE),
  slate = Color(0xFF525E72),
  surface = Color(0xFFF8F9FC),
  card = Color(0xFFEEF1F6),
  outline = Color(0xFFD7DCE6),
  amber = Color(0xFFFFBA4E),
  green = Color(0xFF276B39),
  ink = Color(0xFF0E1726),
  panel = Color(0xFFFFFFFF),
  heading = Color(0xFF001849),
)

internal val DarkLandingColors = LandingColors(
  navy = Color(0xFF06101F),
  navy2 = Color(0xFF04205C),
  blue = Color(0xFF5B9BE8),
  blueBright = Color(0xFF3B82E0),
  sky = Color(0xFFD5E3FF),
  skyDim = Color(0xFFAEC4EA),
  slate = Color(0xFF9AA7BD),
  surface = Color(0xFF0B1120),
  card = Color(0xFF111A2C),
  outline = Color(0xFF26324A),
  amber = Color(0xFFFFBA4E),
  green = Color(0xFF276B39),
  ink = Color(0xFFE7EDF8),
  panel = Color(0xFF161F33),
  heading = Color(0xFFF2F6FF),
)

/** Builds a single-color stroke icon (Lucide style) from one or more raw SVG path `d` strings. */
private fun strokeIcon(
  strokeWidth: Float = 2f,
  vararg pathData: String,
): ImageVector {
  val builder = ImageVector.Builder(
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  )
  for (d in pathData) {
    builder.addPath(
      pathData = PathParser().parsePathString(d)
        .toNodes(),
      fill = null,
      stroke = SolidColor(Color.Black),
      strokeLineWidth = strokeWidth,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    )
  }
  return builder.build()
}

/** Chevron used by the FAQ disclosure rows. */
internal val IconChevronDown: ImageVector = strokeIcon(2f, "m6 9 6 6 6-6")

/** Envelope used by the "Log in with email" button. */
internal val IconMail: ImageVector = strokeIcon(
  2f,
  "M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z",
  "m22 6-10 7L2 6",
)

/** Feature 1 — inspection & service-bulletin tracking (check inside an open card). */
internal val IconInspection: ImageVector = strokeIcon(
  1.8f,
  "M9 11l3 3L22 4",
  "M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11",
)

/** Feature 2 — squawk log (warning triangle). */
internal val IconSquawk: ImageVector = strokeIcon(
  1.8f,
  "M10.3 4.3L2 19a1.5 1.5 0 0 0 1.3 2.2h17.4A1.5 1.5 0 0 0 22 19L13.7 4.3a1.5 1.5 0 0 0-2.6 0z",
  "M12 9v5M12 17.5v.5",
)

/** Feature — every kind of thing (stacked layers). */
internal val IconLayers: ImageVector = strokeIcon(
  1.8f,
  "m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z",
  "m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65",
  "m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65",
)

/** Feature — collaboration (two people). */
internal val IconPeople: ImageVector = strokeIcon(
  1.8f,
  "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
  "M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  "M22 21v-2a4 4 0 0 0-3-3.87",
  "M16 3.13a4 4 0 0 1 0 7.75",
)

/** Feature — starter schedules (a checked list). */
internal val IconListChecks: ImageVector = strokeIcon(
  1.8f,
  "m3 17 2 2 4-4",
  "m3 7 2 2 4-4",
  "M13 6h8",
  "M13 12h8",
  "M13 18h8",
)

/** Feature — attachments and export (paperclip). */
internal val IconPaperclip: ImageVector = strokeIcon(
  1.8f,
  "m21.44 11.05-9.19 9.19a6 6 0 0 1-8.49-8.49l8.57-8.57A4 4 0 1 1 18 8.84l-8.59 8.57a2 2 0 0 1-2.83-2.83l8.49-8.48",
)

/** Feature 3 — on the web today (monitor). */
internal val IconMonitor: ImageVector = strokeIcon(
  1.8f,
  "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
  "M8 21h8M12 17v4",
)

/** Feature — offline first (a cloud with a slash, the sync that happens later). */
internal val IconOffline: ImageVector = strokeIcon(
  1.8f,
  "m2 2 20 20",
  "M5.782 5.782A7 7 0 0 0 9 19h8.5a4.5 4.5 0 0 0 1.307-.193",
  "M21.532 16.5A4.5 4.5 0 0 0 17.5 10h-1.79A7.008 7.008 0 0 0 10 5.07",
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

/** The Google Play triangle in its four brand colours. */
internal val GooglePlayLogo: ImageVector = ImageVector.Builder(
  defaultWidth = 24.dp,
  defaultHeight = 24.dp,
  viewportWidth = 24f,
  viewportHeight = 24f,
)
  .apply {
    addPath(
      PathParser().parsePathString("M3.6 1.8 13.9 12 3.6 22.2c-.4-.2-.6-.6-.6-1.1V2.9c0-.5.2-.9.6-1.1z")
        .toNodes(),
      fill = SolidColor(Color(0xFF00D7FE)),
    )
    addPath(
      PathParser().parsePathString("M17.4 8.5 13.9 12 3.6 1.8c.3-.2.8-.2 1.2.1L17.4 8.5z")
        .toNodes(),
      fill = SolidColor(Color(0xFF00F076)),
    )
    addPath(
      PathParser().parsePathString("M17.4 15.5 4.8 22.1c-.4.3-.9.3-1.2.1L13.9 12l3.5 3.5z")
        .toNodes(),
      fill = SolidColor(Color(0xFFFF3A44)),
    )
    addPath(
      PathParser().parsePathString(
        "M21.2 10.6c.9.5.9 2.3 0 2.8l-3.8 2.1L13.9 12l3.5-3.5 3.8 2.1z"
      )
        .toNodes(),
      fill = SolidColor(Color(0xFFFFD500)),
    )
  }
  .build()
