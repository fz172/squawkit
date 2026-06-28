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
      pathData = PathParser().parsePathString(d).toNodes(),
      fill = null,
      stroke = SolidColor(Color.Black),
      strokeLineWidth = strokeWidth,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    )
  }
  return builder.build()
}

/** Check mark used in the hero trust list. */
internal val IconCheck: ImageVector = strokeIcon(2f, "M20 6 9 17l-5-5")

/** Chevron used by the FAQ disclosure rows. */
internal val IconChevronDown: ImageVector = strokeIcon(2f, "m6 9 6 6 6-6")

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

/** Feature 3 — on the web today (monitor). */
internal val IconMonitor: ImageVector = strokeIcon(
  1.8f,
  "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
  "M8 21h8M12 17v4",
)

/**
 * The SquawkIt brand plane as a single-color vector, cropped tight from the app's
 * `ic_launcher_foreground`. The launcher foreground carries a large built-in safe-zone margin (the
 * plane fills only ~40% of its 1024² box); here the group transform is baked in and the viewport is
 * cropped to the artwork's square bounding box, so `Icon(BrandPlane, Modifier.size(n))` renders the
 * plane crisply at full size with no rasterizing `Modifier.scale`. Tinted via [Icon].
 */
private val BrandPlanePaths = listOf(
  "M635.3,642.7C616.3,605.9 597.6,569.5 578.7,533.1C573.5,523.2 573.4,523.4 563.5,528.5C543.5,538.9 523.3,548.7 502.2,556.6C495.7,559.1 495.6,559.3 497.7,566.1C505.3,590.2 512.9,614.3 520.4,638.5C524.6,652 522.5,664.3 511.9,674.2C496.5,688.6 472.8,687 459.1,670.2C444,651.6 429.6,632.4 414.8,613.4C413.5,611.7 412.2,610 410.9,608.3C405.1,600.6 399.2,594.4 388.6,591.8C370.2,587.3 360.9,569.7 364.4,551C365,547.7 364,545.4 361.7,543C346.8,527.8 332,512.5 317.2,497.1C312.9,492.6 308.7,488.1 304.9,483.2C292,466.6 297.9,435 325,429.2C331,427.9 337.3,427.9 343.3,430.1C362.3,437.2 380.5,446.4 399.3,454C403.5,455.7 407.6,457.5 411.7,459.3C414.4,460.5 416,460 417.8,457.3C433.7,433.1 450.5,409.5 469.7,387.7C473.1,383.8 472.6,382.1 467.5,379.4C445.7,367.8 423.9,356.2 402.2,344.6C375.4,330.3 348.7,316 322,301.6C307.6,293.8 297.2,282.7 295.3,265.9C292.8,245.3 303.5,222.6 331.5,219.7C337.5,219.1 342.9,221.5 348.3,223.7C385.3,239 422.2,254.2 459.1,269.5C488.6,281.8 518.1,294.2 547.5,306.8C552.2,308.8 554.7,308 557.9,304.1C580.2,277.2 604.2,252 631.7,230.4C650.7,215.5 671.8,204.8 696,201.1C748.6,193.1 801,223.4 813.7,276.2C821.9,310.3 812.7,341.1 791.3,368.4C775.4,388.6 754.7,403.3 733.5,417.3C713.3,430.8 691.9,442.4 670.4,453.8C665.7,456.2 664.9,458.8 666.4,463.6C682.7,514.3 698.8,565.1 714.9,615.8C719.8,631.2 723.9,646.9 729.4,662C735.4,678.6 724.5,699.3 705.6,705.2C687.2,711 668.6,704.4 658.7,687.3C650.3,672.9 643.2,657.8 635.3,642.7M749.7,254.8C737.8,248.7 725.3,246.1 712,247.3C700.2,248.4 693.2,256 693.3,267.6C693.5,276.6 696.9,284.5 701.2,292.1C712.1,311.1 727.4,324.8 749.1,330C759.1,332.5 766.8,328.4 771.1,319.1C773,315 773.6,310.5 774.1,306C776.6,286.3 769.8,265.2 749.7,254.8z",
  "M342.5,593.2C355.7,600.5 358,613.8 347.7,624.1C323.1,648.8 298.6,673.8 273.1,697.8C261.9,708.4 246.5,705.3 241.7,691.4C239,683.7 240.6,676.8 246.6,670.8C265,652.7 283.1,634.4 301.4,616.1C307.2,610.2 313.1,604.3 318.9,598.3C325.6,591.5 333.2,589.7 342.5,593.2z",
  "M387.4,703.4C378.1,712.7 369,721.8 359.9,730.8C351.2,739.3 339.4,739.5 331.4,731.4C323.5,723.3 323.7,712.1 332.3,703.3C346.1,689.2 360.4,675.4 374,661C382.2,652.5 394,654.2 401.4,660.8C409.2,667.8 409.7,679.9 402.3,688.2C397.7,693.4 392.5,698.2 387.4,703.4z",
  "M715.2,471.9C741.2,464.6 765.1,481.9 766.1,508.4C766.4,518.2 762.6,526.8 755.9,533.7C747.8,541.9 739.3,549.6 731,557.6C730.4,558.1 729.7,558.6 728.2,558.2C726.1,552 723.8,545.5 721.6,538.9C716,521.5 710.5,504.1 704.9,486.7C702.3,478.6 702.3,478.5 709.9,474.3C711.4,473.4 713.2,472.8 715.2,471.9z",
  "M489.1,207.9C512.2,196.8 543.8,211.4 546.1,242.1C546.7,250.5 543.7,258.1 539.5,265.2C537.9,267.8 536,267.5 533.5,266.5C511.9,257.7 490.3,249.1 468.7,240.4C465.5,239.2 462.1,238.4 458.2,235.6C468.6,226.3 476.4,214.8 489.1,207.9z",
  "M228.8,599.3C219.2,589.7 218.7,579.2 227.7,570.1C240.7,556.9 253.7,543.9 267.1,531.1C277.9,520.8 294.1,524.4 299.3,538.1C301.8,544.6 301.1,551.3 296.2,556.3C282.4,570.7 269.2,585.8 253.9,598.7C245.9,605.3 237.6,605.3 228.8,599.3z",
)

/**
 * Tight square viewport (artwork bbox 429.215²) + baked group transform (launcher scale 0.7115625,
 * translation offsets to crop the safe-zone padding and re-center). See [BrandPlanePaths].
 */
internal val BrandPlane: ImageVector = ImageVector.Builder(
  defaultWidth = 24.dp,
  defaultHeight = 24.dp,
  viewportWidth = 429.215f,
  viewportHeight = 429.215f,
).apply {
  addGroup(
    scaleX = 0.7115625f,
    scaleY = 0.7115625f,
    translationX = -155.6187f,
    translationY = -117.1943f,
  )
  for (d in BrandPlanePaths) {
    addPath(
      pathData = PathParser().parsePathString(d).toNodes(),
      fill = SolidColor(Color.Black),
    )
  }
  clearGroup()
}.build()

/** The multi-color Google "G" mark (rendered with Image, not tinted). */
internal val GoogleLogo: ImageVector = ImageVector.Builder(
  defaultWidth = 48.dp,
  defaultHeight = 48.dp,
  viewportWidth = 48f,
  viewportHeight = 48f,
).apply {
  addPath(
    PathParser().parsePathString(
      "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
    ).toNodes(),
    fill = SolidColor(Color(0xFFEA4335)),
  )
  addPath(
    PathParser().parsePathString(
      "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
    ).toNodes(),
    fill = SolidColor(Color(0xFF4285F4)),
  )
  addPath(
    PathParser().parsePathString(
      "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
    ).toNodes(),
    fill = SolidColor(Color(0xFFFBBC05)),
  )
  addPath(
    PathParser().parsePathString(
      "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
    ).toNodes(),
    fill = SolidColor(Color(0xFF34A853)),
  )
}.build()

/** The Apple mark (tinted to the button's content color via Icon). */
internal val AppleLogo: ImageVector = ImageVector.Builder(
  defaultWidth = 24.dp,
  defaultHeight = 24.dp,
  viewportWidth = 24f,
  viewportHeight = 24f,
).apply {
  addPath(
    PathParser().parsePathString(
      "M17.05 12.04c-.03-2.8 2.29-4.15 2.39-4.21-1.3-1.9-3.32-2.16-4.04-2.19-1.72-.17-3.36 1.01-4.23 1.01-.87 0-2.21-.99-3.64-.96-1.87.03-3.6 1.09-4.56 2.76-1.95 3.38-.5 8.38 1.4 11.12.93 1.34 2.03 2.85 3.47 2.8 1.39-.06 1.92-.9 3.6-.9 1.68 0 2.16.9 3.64.87 1.5-.03 2.45-1.37 3.37-2.72 1.06-1.56 1.5-3.07 1.52-3.15-.03-.01-2.92-1.12-2.95-4.44zM14.28 3.7c.77-.93 1.29-2.22 1.15-3.5-1.11.04-2.46.74-3.25 1.67-.71.82-1.33 2.14-1.16 3.4 1.24.1 2.5-.63 3.26-1.57z",
    ).toNodes(),
    fill = SolidColor(Color.Black),
  )
}.build()
