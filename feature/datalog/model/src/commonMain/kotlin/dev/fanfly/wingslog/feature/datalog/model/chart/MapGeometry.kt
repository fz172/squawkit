package dev.fanfly.wingslog.feature.datalog.model.chart

import dev.fanfly.wingslog.feature.datalog.model.PositionColumn
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

/**
 * Web Mercator, the projection every raster tile scheme is cut in, normalized so the whole world is
 * 0..1 on each axis at every zoom (design §11.6). Multiply by the world size in pixels to land in
 * screen space.
 */
object WebMercator {
  /** Mercator diverges at the poles; every tile scheme cuts the world off here. */
  const val MAX_LATITUDE = 85.05112878

  fun normalizedX(longitudeDeg: Double): Double = (longitudeDeg + 180.0) / 360.0

  fun normalizedY(latitudeDeg: Double): Double {
    val radians = latitudeDeg.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / 180.0
    // At the limit itself the arithmetic lands a few parts in 1e12 outside the world; a tile index
    // derived from that underflows, so the range is enforced rather than assumed.
    return ((1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0).coerceIn(
      0.0,
      1.0
    )
  }
}

/** A track's extent in normalized Mercator space. */
data class MapBounds(
  val minX: Double,
  val minY: Double,
  val maxX: Double,
  val maxY: Double,
) {
  val centerX: Double get() = (minX + maxX) / 2.0
  val centerY: Double get() = (minY + maxY) / 2.0
  val spanX: Double get() = maxX - minX
  val spanY: Double get() = maxY - minY
}

/** The extent of every row that had a fix, or null when the log has none. */
fun PositionColumn.bounds(): MapBounds? {
  var minX = Double.MAX_VALUE
  var minY = Double.MAX_VALUE
  var maxX = -Double.MAX_VALUE
  var maxY = -Double.MAX_VALUE
  var any = false
  for (i in latitude.indices) {
    val lat = latitude[i]
    val lon = longitude[i]
    if (lat.isNaN() || lon.isNaN()) continue
    val x = WebMercator.normalizedX(lon)
    val y = WebMercator.normalizedY(lat)
    if (x < minX) minX = x
    if (x > maxX) maxX = x
    if (y < minY) minY = y
    if (y > maxY) maxY = y
    any = true
  }
  return if (any) MapBounds(minX, minY, maxX, maxY) else null
}

/** Where the world sits behind the pane: the tile zoom to fetch and the top-left corner on screen. */
data class MapViewport(
  /** The zoom whose tiles to fetch. Below the camera's own scale when the camera is over-zoomed. */
  val zoom: Int,
  /** The whole world's width in screen pixels. Continuous, so it is not tied to [zoom]. */
  val worldSizePx: Double,
  val leftPx: Double,
  val topPx: Double,
) {
  fun screenX(normalizedX: Double): Float =
    (normalizedX * worldSizePx - leftPx).toFloat()

  fun screenY(normalizedY: Double): Float =
    (normalizedY * worldSizePx - topPx).toFloat()

  /** A tile's size on screen: bigger than the source tile once the camera is over-zoomed. */
  fun tileScreenSizePx(): Double = worldSizePx / (1 shl zoom)
}

/** One tile to fetch, where to draw it, and how big to draw it. */
data class MapTile(
  val zoom: Int,
  val x: Int,
  val y: Int,
  val leftPx: Float,
  val topPx: Float,
  val sizePx: Float,
)

/**
 * What the user is looking at: a centre in normalized Mercator space and a continuous world size.
 *
 * Scale is continuous rather than a tile zoom because a taxi track is a few dozen metres across and
 * the tile schemes stop at zoom 19; pinned to integer zooms such a track renders as a speck. The
 * camera keeps zooming and the tiles are stretched, which is blurry but legible — the alternative
 * is a correct-looking map of nothing.
 */
data class MapCamera(
  val centerX: Double,
  val centerY: Double,
  val worldSizePx: Double,
) {
  /** Pans by a screen-space drag. */
  fun panBy(dxPx: Float, dyPx: Float): MapCamera = copy(
    centerX = (centerX - dxPx / worldSizePx).coerceIn(0.0, 1.0),
    centerY = (centerY - dyPx / worldSizePx).coerceIn(0.0, 1.0),
  )

  /**
   * Scales by [factor] about [focusX], [focusY] in the pane, so whatever is under the fingers stays
   * under them. [limits] stops the world from shrinking below one tile or stretching past the
   * over-zoom the provider's deepest tiles can carry.
   */
  fun scaleBy(
    factor: Float,
    focusX: Float,
    focusY: Float,
    widthPx: Float,
    heightPx: Float,
    limits: ClosedRange<Double>,
  ): MapCamera {
    val next =
      (worldSizePx * factor).coerceIn(limits.start, limits.endInclusive)
    if (next == worldSizePx) return this
    // The focus holds still: its normalized position must land on the same pixel afterwards.
    val focusNormalizedX =
      (centerX * worldSizePx - widthPx / 2.0 + focusX) / worldSizePx
    val focusNormalizedY =
      (centerY * worldSizePx - heightPx / 2.0 + focusY) / worldSizePx
    return MapCamera(
      centerX = (focusNormalizedX + (widthPx / 2.0 - focusX) / next).coerceIn(
        0.0,
        1.0
      ),
      centerY = (focusNormalizedY + (heightPx / 2.0 - focusY) / next).coerceIn(
        0.0,
        1.0
      ),
      worldSizePx = next,
    )
  }

  /** The viewport this camera shows in a [widthPx] × [heightPx] pane. */
  fun viewport(
    widthPx: Float,
    heightPx: Float,
    tileSizePx: Int,
    maxZoom: Int
  ): MapViewport {
    val ideal = floor(log2(worldSizePx / tileSizePx)).toInt()
    return MapViewport(
      zoom = ideal.coerceIn(0, maxZoom),
      worldSizePx = worldSizePx,
      leftPx = centerX * worldSizePx - widthPx / 2.0,
      topPx = centerY * worldSizePx - heightPx / 2.0,
    )
  }
}

/** How far the camera may scale: one tile for the whole world, up to [overZoom] past [maxZoom]. */
fun scaleLimits(
  tileSizePx: Int,
  maxZoom: Int,
  overZoom: Int
): ClosedRange<Double> =
  tileSizePx.toDouble()..(tileSizePx.toDouble() * 2.0.pow(maxZoom + overZoom))

/**
 * The camera that shows [bounds] filling [fitFraction] of a [widthPx] × [heightPx] pane.
 *
 * The scale is continuous, so the track fills the pane whatever its size; a track that never moved
 * has no extent to fit and opens at the deepest scale [limits] allows.
 */
fun fitCamera(
  bounds: MapBounds,
  widthPx: Float,
  heightPx: Float,
  fitFraction: Double,
  limits: ClosedRange<Double>,
): MapCamera {
  val byWidth =
    if (bounds.spanX > 0.0) fitFraction * widthPx / bounds.spanX else Double.MAX_VALUE
  val byHeight =
    if (bounds.spanY > 0.0) fitFraction * heightPx / bounds.spanY else Double.MAX_VALUE
  val world = min(byWidth, byHeight)
  return MapCamera(
    centerX = bounds.centerX,
    centerY = bounds.centerY,
    worldSizePx = (if (world == Double.MAX_VALUE) limits.endInclusive else world)
      .coerceIn(limits.start, limits.endInclusive),
  )
}

/** Every tile covering the pane, clamped to the ones that exist at this zoom. */
fun MapViewport.tiles(widthPx: Float, heightPx: Float): List<MapTile> {
  if (widthPx <= 0f || heightPx <= 0f) return emptyList()
  val tileScreen = tileScreenSizePx()
  if (tileScreen <= 0.0) return emptyList()
  val last = (1 shl zoom) - 1
  val firstX = floor(leftPx / tileScreen).toInt()
  val lastX = floor((leftPx + widthPx) / tileScreen).toInt()
  val firstY = floor(topPx / tileScreen).toInt()
  val lastY = floor((topPx + heightPx) / tileScreen).toInt()
  val tiles = ArrayList<MapTile>()
  for (y in firstY..lastY) {
    if (y < 0 || y > last) continue
    for (x in firstX..lastX) {
      if (x < 0 || x > last) continue
      tiles += MapTile(
        zoom = zoom,
        x = x,
        y = y,
        leftPx = (x * tileScreen - leftPx).toFloat(),
        topPx = (y * tileScreen - topPx).toFloat(),
        sizePx = tileScreen.toFloat(),
      )
    }
  }
  return tiles
}
