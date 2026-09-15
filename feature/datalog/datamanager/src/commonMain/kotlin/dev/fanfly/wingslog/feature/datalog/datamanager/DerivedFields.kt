package dev.fanfly.wingslog.feature.datalog.datamanager

import dev.fanfly.wingslog.feature.datalog.model.CanonicalSeries
import dev.fanfly.wingslog.feature.datalog.model.ParsedDataLog

/** Record flags computed from a parsed log, written against canonical ids so they survive a format change. */
object DerivedFields {

  private const val AIRBORNE_GROUND_SPEED_KT = 30f
  private const val AIRBORNE_AGL_FT = 50f

  /** PRD R13: false renders as "Ground run". */
  fun airborne(parsed: ParsedDataLog): Boolean =
    parsed.anyAbove(CanonicalSeries.GROUND_SPEED, AIRBORNE_GROUND_SPEED_KT) ||
      parsed.anyAbove(CanonicalSeries.AGL, AIRBORNE_AGL_FT)

  /** Latitude and longitude of the last row with a fix, or null when the log has none. */
  fun endPosition(parsed: ParsedDataLog): Pair<Double, Double>? {
    val position = parsed.data.position ?: return null
    for (i in position.latitude.indices.reversed()) {
      val lat = position.latitude[i]
      val lon = position.longitude[i]
      if (!lat.isNaN() && !lon.isNaN()) return lat to lon
    }
    return null
  }

  /** PRD R36: the ident a Garmin recorder puts in `log_<date>_<time>_<ident>.csv`, else "". */
  fun startLocationIdent(fileName: String): String =
    FILE_NAME_IDENT.matchEntire(fileName.trim())?.groupValues?.get(1) ?: ""

  /**
   * PRD R11: the recorder's `aircraft_ident` against the Thing's identifier, compared without case
   * or punctuation. False when either side is blank — an unknown is not a mismatch.
   */
  fun identityMismatch(
    recorderIdentity: String,
    thingIdentifier: String?
  ): Boolean {
    val a = recorderIdentity.normalised()
    val b = thingIdentifier.orEmpty()
      .normalised()
    return a.isNotEmpty() && b.isNotEmpty() && a != b
  }

  private fun String.normalised(): String =
    filter { it.isLetterOrDigit() }.uppercase()

  private fun ParsedDataLog.anyAbove(
    canonicalId: String,
    threshold: Float
  ): Boolean {
    val column = series.firstOrNull { it.canonical_id == canonicalId }?.column
      ?: return false
    val values = data.numeric[column]?.raw ?: return false
    return values.any { !it.isNaN() && it > threshold }
  }

  // A G3X writes an eight-digit date, a G1000 a six-digit one: log_20260902_144756_KXYZ.csv and
  // log_240810_104802_KXYZ.csv are the same filename in two dialects.
  private val FILE_NAME_IDENT =
    Regex("""^log_\d{6}(?:\d{2})?_\d{6}_([A-Za-z0-9]+)\.csv$""", RegexOption.IGNORE_CASE)
}
