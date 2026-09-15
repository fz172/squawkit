package dev.fanfly.wingslog.feature.datalog.model

/**
 * Where map tiles come from and what must be shown for them (design §11.6). Bound in Koin so a
 * host can swap providers without the pane knowing.
 *
 * [attribution] is not optional: every free raster provider requires it, and it renders in the
 * pane's corner.
 */
data class MapTileProvider(
  val urlTemplate: String,
  val attribution: String,
  val tileSizePx: Int,
  val maxZoom: Int,
) {
  fun tileUrl(zoom: Int, x: Int, y: Int): String = urlTemplate
    .replace("{z}", zoom.toString())
    .replace("{x}", x.toString())
    .replace("{y}", y.toString())

  companion object {
    /**
     * OpenStreetMap's own tiles. Their usage policy requires an identifying User-Agent and forbids
     * bulk downloading, so the pane fetches only the tiles it shows and the client sends
     * [OSM_USER_AGENT]. A heavier deployment should move to a paid provider rather than lean on
     * this one.
     */
    val OpenStreetMap = MapTileProvider(
      urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
      attribution = "© OpenStreetMap contributors",
      tileSizePx = 256,
      maxZoom = 19,
    )

    const val OSM_USER_AGENT = "SquawkIt/1.0 (+https://squawkit.fanfly.dev)"
  }
}
