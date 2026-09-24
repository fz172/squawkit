package dev.fanfly.wingslog.feature.datalog.viewing.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.ImageRequest
import dev.fanfly.wingslog.core.ui.layout.LocalLayoutTier
import dev.fanfly.wingslog.core.ui.theme.Spacing
import dev.fanfly.wingslog.core.ui.theme.WingslogTypography
import dev.fanfly.wingslog.feature.datalog.model.MapTileProvider
import dev.fanfly.wingslog.feature.datalog.model.PositionColumn
import dev.fanfly.wingslog.feature.datalog.model.ViewWindow
import dev.fanfly.wingslog.feature.datalog.model.chart.MapCamera
import dev.fanfly.wingslog.feature.datalog.model.chart.MapViewport
import dev.fanfly.wingslog.feature.datalog.model.chart.WebMercator
import dev.fanfly.wingslog.feature.datalog.model.chart.bounds
import dev.fanfly.wingslog.feature.datalog.model.chart.fitCamera
import dev.fanfly.wingslog.feature.datalog.model.chart.scaleLimits
import dev.fanfly.wingslog.feature.datalog.model.chart.tiles
import io.ktor.client.HttpClient
import io.ktor.client.plugins.UserAgent

/** The map is the pane that answers "where", and a route needs room; design §11.7 heights × this. */
private const val MAP_HEIGHT_FACTOR = 1.5f

/** The track's bounding box fills this much of the pane, leaving the ends off the edge. */
private const val FIT_FRACTION = 0.8

private val TrackStroke = 2.dp
private val TrackStrokeOutsideWindow = 1.dp
private val CursorDotRadius = 5.dp
private const val OUTSIDE_WINDOW_ALPHA = 0.35f

/**
 * How far past the provider's deepest tiles the camera may still zoom. A taxi track is tens of
 * metres across and tiles stop at zoom 19, so without this it renders as a speck.
 */
private const val MAX_OVER_ZOOM = 3

/** A wheel notch's share of a zoom step, matching the chart's own scroll handling. */
private const val SCROLL_ZOOM_STEP = 0.1f

/**
 * The most one wheel event may scale by. Browsers report wildly different deltas per notch — a
 * trackpad flick arrives as one huge one — and without this a single gesture jumps from the fitted
 * view to the zoom ceiling.
 */
private const val MAX_SCROLL_FACTOR = 2f

/**
 * The map pane (PRD R29, design §11.6): raster tiles under the track, a dot at the cursor, and the
 * provider's attribution in the corner.
 *
 * Tiles are fetched one by one through Coil, so a tile that fails leaves the pane's own surface
 * showing and the track still draws over it — R29's degrade rule, and what happens offline.
 *
 * The camera opens fitted to the whole track and is the user's from then on: pinch or wheel to
 * zoom, drag to pan. It does not follow the charts' brushed window, but the part of the track
 * inside that window is drawn full strength and the rest dimmed, so the shared time domain still
 * reads here.
 */
@Composable
fun MapPane(
  position: PositionColumn,
  timeSeconds: IntArray,
  durationSeconds: Int,
  view: ViewWindow?,
  cursorIndex: Int,
  isTarget: Boolean,
  provider: MapTileProvider,
  modifier: Modifier = Modifier,
) {
  val height =
    (if (LocalLayoutTier.current.isCompact) PaneHeightCompact else PaneHeightWide) * MAP_HEIGHT_FACTOR
  val borderColor =
    if (isTarget) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
  val surface = MaterialTheme.colorScheme.surface
  // The same color its chip carries, rather than the theme's primary: the trail and the chip that
  // names it should agree, and a blue trail competes with every road and river on the basemap.
  val trackColor = SeriesPalette.MAP_TRACK
  val cursorColor = MaterialTheme.colorScheme.tertiary
  val density = LocalDensity.current

  val platformContext = LocalPlatformContext.current
  // One loader per pane, carrying the User-Agent OpenStreetMap's policy requires. Coil's default
  // client sends its own, which tile servers reject.
  val imageLoader = remember(platformContext) {
    ImageLoader.Builder(platformContext)
      .components {
        add(
          KtorNetworkFetcherFactory(
            httpClient = {
              HttpClient {
                install(UserAgent) { agent = MapTileProvider.OSM_USER_AGENT }
              }
            })
        )
      }
      .build()
  }

  val trackBounds = remember(position) { position.bounds() }
  var size by remember { mutableStateOf(IntSize.Zero) }
  val limits = remember(provider) {
    scaleLimits(
      provider.tileSizePx, provider.maxZoom, MAX_OVER_ZOOM
    )
  }
  // Null until the pane has been measured; the first measure fits the track, and every gesture
  // after that is the user's, so a recomposition must not snap the view back.
  var camera by remember(
    position, provider
  ) { mutableStateOf<MapCamera?>(null) }

  Box(
    modifier = modifier.fillMaxWidth()
      .height(height)
      .clip(RoundedCornerShape(Spacing.cardCornerRadius))
      .border(
        Spacing.hairline,
        borderColor,
        RoundedCornerShape(Spacing.cardCornerRadius)
      )
      .onSizeChanged { size = it }
      .pointerInput(provider, limits) {
        detectTransformGestures { centroid, pan, zoom, _ ->
          val current = camera ?: return@detectTransformGestures
          val panned = current.panBy(pan.x, pan.y)
          camera = if (zoom == 1f) panned else panned.scaleBy(
            factor = zoom,
            focusX = centroid.x,
            focusY = centroid.y,
            widthPx = this.size.width.toFloat(),
            heightPx = this.size.height.toFloat(),
            limits = limits,
          )
        }
      }
      // A mouse has no pinch: the wheel zooms about the pointer, as it does on the charts. A scroll
      // starts no gesture, so it needs its own loop rather than awaitEachGesture.
      .pointerInput(provider, limits) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.first()
            val current = camera ?: continue
            val factor =
              (1f - change.scrollDelta.y * SCROLL_ZOOM_STEP).coerceIn(
                1f / MAX_SCROLL_FACTOR,
                MAX_SCROLL_FACTOR
              )
            camera = current.scaleBy(
              factor = factor,
              focusX = change.position.x,
              focusY = change.position.y,
              widthPx = size.width.toFloat(),
              heightPx = size.height.toFloat(),
              limits = limits,
            )
            change.consume()
          }
        }
      },
  ) {
    if (trackBounds != null && camera == null && size.width > 0 && size.height > 0) {
      camera = fitCamera(
        trackBounds,
        size.width.toFloat(),
        size.height.toFloat(),
        FIT_FRACTION,
        limits
      )
    }
    val viewport: MapViewport? =
      camera?.takeIf { size.width > 0 && size.height > 0 }
        ?.viewport(
          widthPx = size.width.toFloat(),
          heightPx = size.height.toFloat(),
          tileSizePx = provider.tileSizePx,
          maxZoom = provider.maxZoom,
        )

    Canvas(Modifier.fillMaxSize()) { drawRect(surface) }

    if (viewport != null) {
      val tiles = remember(viewport, size) {
        viewport.tiles(size.width.toFloat(), size.height.toFloat())
      }
      tiles.forEach { tile ->
        val painter = rememberAsyncImagePainter(
          model = ImageRequest.Builder(platformContext)
            .data(provider.tileUrl(tile.zoom, tile.x, tile.y))
            .build(),
          imageLoader = imageLoader,
        )
        Image(
          painter = painter,
          contentDescription = null,
          contentScale = ContentScale.FillBounds,
          modifier = Modifier
            // Over-zoomed tiles are drawn larger than they were cut, which is blurry but keeps a
            // short track legible; ContentScale.FillBounds does the stretching.
            .size(with(density) { tile.sizePx.toDp() })
            .offset(
              x = with(density) { tile.leftPx.toDp() },
              y = with(density) { tile.topPx.toDp() },
            ),
        )
      }

      Canvas(Modifier.fillMaxSize()) {
        val window = view
        val inWindow = { index: Int ->
          window == null || (timeSeconds.getOrNull(index)
            ?.let { it >= window.startSeconds && it <= window.endSeconds } == true)
        }
        drawTrack(
          position,
          viewport,
          inWindow = { false },
          color = trackColor.copy(alpha = OUTSIDE_WINDOW_ALPHA),
          strokeWidth = TrackStrokeOutsideWindow.toPx()
        )
        if (window != null) {
          drawTrack(
            position,
            viewport,
            inWindow = inWindow,
            color = trackColor,
            strokeWidth = TrackStroke.toPx()
          )
        } else {
          drawTrack(
            position,
            viewport,
            inWindow = { true },
            color = trackColor,
            strokeWidth = TrackStroke.toPx()
          )
        }
        if (cursorIndex >= 0 && cursorIndex < position.latitude.size) {
          val lat = position.latitude[cursorIndex]
          val lon = position.longitude[cursorIndex]
          if (!lat.isNaN() && !lon.isNaN()) {
            drawCircle(
              color = cursorColor,
              radius = CursorDotRadius.toPx(),
              center = Offset(
                viewport.screenX(WebMercator.normalizedX(lon)),
                viewport.screenY(WebMercator.normalizedY(lat)),
              ),
            )
          }
        }
      }
    }

    Text(
      text = provider.attribution,
      style = WingslogTypography.dataSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.align(Alignment.BottomEnd)
        .padding(Spacing.extraSmall),
    )
  }
}
