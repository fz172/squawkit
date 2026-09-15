package dev.fanfly.wingslog.feature.datalog.model.chart

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.datalog.model.PositionColumn
import org.junit.Test
import kotlin.math.abs

class MapGeometryTest {

  private val tile = 256

  @Test
  fun mercatorPutsTheOriginInTheMiddleAndGreenwichOnTheSeam() {
    assertThat(WebMercator.normalizedX(0.0)).isWithin(1e-9).of(0.5)
    assertThat(WebMercator.normalizedX(-180.0)).isWithin(1e-9).of(0.0)
    assertThat(WebMercator.normalizedX(180.0)).isWithin(1e-9).of(1.0)
    assertThat(WebMercator.normalizedY(0.0)).isWithin(1e-9).of(0.5)
    // North is up: a higher latitude is a smaller y.
    assertThat(WebMercator.normalizedY(45.0)).isLessThan(0.5)
    assertThat(WebMercator.normalizedY(-45.0)).isGreaterThan(0.5)
  }

  @Test
  fun latitudesPastTheMercatorLimitClampInsteadOfDiverging() {
    val atLimit = WebMercator.normalizedY(WebMercator.MAX_LATITUDE)
    assertThat(WebMercator.normalizedY(89.9)).isWithin(1e-9).of(atLimit)
    assertThat(atLimit).isAtLeast(0.0)
    assertThat(atLimit).isAtMost(1.0)
  }

  @Test
  fun boundsCoverEveryFixAndIgnoreTheRowsWithout() {
    val position = PositionColumn(
      latitude = doubleArrayOf(Double.NaN, 37.0, 38.0, Double.NaN),
      longitude = doubleArrayOf(Double.NaN, -122.0, -121.0, 5.0),
    )

    val bounds = position.bounds()!!

    assertThat(bounds.minX).isWithin(1e-9).of(WebMercator.normalizedX(-122.0))
    assertThat(bounds.maxX).isWithin(1e-9).of(WebMercator.normalizedX(-121.0))
    // A longitude with no latitude beside it is not a fix, so 5°E is not in the box.
    assertThat(bounds.maxX).isLessThan(WebMercator.normalizedX(5.0))
    assertThat(bounds.minY).isWithin(1e-9).of(WebMercator.normalizedY(38.0))
    assertThat(bounds.maxY).isWithin(1e-9).of(WebMercator.normalizedY(37.0))
  }

  @Test
  fun aLogWithNoFixHasNoBounds() {
    val empty = PositionColumn(doubleArrayOf(Double.NaN), doubleArrayOf(Double.NaN))
    assertThat(empty.bounds()).isNull()
    assertThat(PositionColumn(DoubleArray(0), DoubleArray(0)).bounds()).isNull()
  }

  private val limits = scaleLimits(tileSizePx = 256, maxZoom = 19, overZoom = 4)

  @Test
  fun theFittedCameraCentresTheTrackAndFillsTheFraction() {
    val position = PositionColumn(
      latitude = doubleArrayOf(37.0, 37.2),
      longitude = doubleArrayOf(-122.0, -121.8),
    )
    val bounds = position.bounds()!!
    val width = 800f
    val height = 400f

    val viewport = fitCamera(bounds, width, height, fitFraction = 0.8, limits = limits)
      .viewport(width, height, tile, maxZoom = 19)

    assertThat(viewport.screenX(bounds.centerX)).isWithin(1e-3f).of(width / 2f)
    assertThat(viewport.screenY(bounds.centerY)).isWithin(1e-3f).of(height / 2f)
    // Scale is continuous, so the track fills the fraction exactly on its tighter axis rather than
    // falling back to the next whole tile zoom.
    val spanXPx = abs(viewport.screenX(bounds.maxX) - viewport.screenX(bounds.minX))
    val spanYPx = abs(viewport.screenY(bounds.maxY) - viewport.screenY(bounds.minY))
    assertThat(maxOf(spanXPx / width, spanYPx / height)).isWithin(1e-3f).of(0.8f)
  }

  @Test
  fun aShortTrackKeepsFillingThePaneByOverZoomingTheDeepestTiles() {
    // Forty metres of taxiing: past zoom 19, where the tiles stop.
    val position = PositionColumn(
      latitude = doubleArrayOf(37.0, 37.00036),
      longitude = doubleArrayOf(-122.0, -122.0),
    )
    val bounds = position.bounds()!!
    val height = 300f

    val camera = fitCamera(bounds, 800f, height, fitFraction = 0.8, limits = limits)
    val viewport = camera.viewport(800f, height, tile, maxZoom = 19)

    // The camera zooms past what the tiles carry, so the track still fills the pane...
    val spanYPx = abs(viewport.screenY(bounds.maxY) - viewport.screenY(bounds.minY))
    assertThat(spanYPx).isWithin(1f).of(height * 0.8f)
    // ...while the tiles fetched stay at the deepest zoom that exists, drawn larger than they were cut.
    assertThat(viewport.zoom).isEqualTo(19)
    assertThat(viewport.tileScreenSizePx()).isGreaterThan(tile.toDouble())
  }

  @Test
  fun aTrackThatNeverMovedOpensAtTheDeepestScaleAllowed() {
    val still = PositionColumn(doubleArrayOf(37.0, 37.0), doubleArrayOf(-122.0, -122.0))

    val camera = fitCamera(still.bounds()!!, 800f, 400f, fitFraction = 0.8, limits = limits)

    assertThat(camera.worldSizePx).isEqualTo(limits.endInclusive)
  }

  @Test
  fun pinchingHoldsWhateverIsUnderTheFingers() {
    val camera = MapCamera(centerX = 0.5, centerY = 0.5, worldSizePx = 4096.0)
    val width = 800f
    val height = 400f
    val focusX = 200f
    val focusY = 100f
    val before = camera.viewport(width, height, tile, maxZoom = 19)
    // Whatever normalized point sits under the focus before the pinch.
    val underFinger = (before.leftPx + focusX) / before.worldSizePx
    val underFingerY = (before.topPx + focusY) / before.worldSizePx

    val zoomed = camera.scaleBy(2f, focusX, focusY, width, height, limits)
    val after = zoomed.viewport(width, height, tile, maxZoom = 19)

    assertThat(zoomed.worldSizePx).isWithin(1e-6).of(8192.0)
    assertThat(after.screenX(underFinger)).isWithin(1e-2f).of(focusX)
    assertThat(after.screenY(underFingerY)).isWithin(1e-2f).of(focusY)
  }

  @Test
  fun theCameraStopsAtOneTileOutAndAtTheOverZoomCeiling() {
    val camera = MapCamera(0.5, 0.5, worldSizePx = 4096.0)

    val tiny = camera.scaleBy(0.000001f, 400f, 200f, 800f, 400f, limits)
    assertThat(tiny.worldSizePx).isEqualTo(limits.start)

    val huge = camera.scaleBy(1e9f, 400f, 200f, 800f, 400f, limits)
    assertThat(huge.worldSizePx).isEqualTo(limits.endInclusive)
  }

  @Test
  fun draggingMovesTheCentreTheOtherWayAndStopsAtTheEdgeOfTheWorld() {
    val camera = MapCamera(centerX = 0.5, centerY = 0.5, worldSizePx = 1000.0)

    // Dragging the map right moves the centre left by the same share of the world.
    val panned = camera.panBy(100f, 0f)
    assertThat(panned.centerX).isWithin(1e-9).of(0.4)

    assertThat(camera.panBy(-1e6f, -1e6f).centerX).isEqualTo(1.0)
    assertThat(camera.panBy(1e6f, 1e6f).centerY).isEqualTo(0.0)
  }

  @Test
  fun tilesCoverThePaneAndStopAtTheEdgeOfTheWorld() {
    val viewport = MapViewport(zoom = 1, worldSizePx = 512.0, leftPx = 0.0, topPx = 0.0)

    val tiles = viewport.tiles(widthPx = 512f, heightPx = 512f)

    // Zoom 1 is a 2 × 2 world and the pane shows all of it.
    assertThat(tiles).hasSize(4)
    assertThat(tiles.map { it.x to it.y })
      .containsExactly(0 to 0, 1 to 0, 0 to 1, 1 to 1)
    assertThat(tiles.first { it.x == 1 && it.y == 1 }.leftPx).isWithin(1e-3f).of(256f)
    assertThat(tiles.first().sizePx).isWithin(1e-3f).of(256f)
  }

  @Test
  fun tilesOffTheWorldAreNotRequested() {
    // Scrolled past the north-west corner: the tiles that would sit there do not exist.
    val viewport = MapViewport(zoom = 1, worldSizePx = 512.0, leftPx = -300.0, topPx = -300.0)

    val tiles = viewport.tiles(widthPx = 400f, heightPx = 400f)

    assertThat(tiles.none { it.x < 0 || it.y < 0 }).isTrue()
    assertThat(tiles.map { it.x to it.y }).containsExactly(0 to 0)
    assertThat(viewport.tiles(0f, 0f)).isEmpty()
  }
}
