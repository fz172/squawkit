package dev.fanfly.wingslog.feature.datalog.datamanager

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.datalog.datamanager.avidyne.AvidyneParser
import dev.fanfly.wingslog.feature.datalog.datamanager.dynon.DynonParser
import dev.fanfly.wingslog.feature.datalog.datamanager.garmin.GarminParser
import org.junit.Test

/**
 * Every parser's version, pinned in one place.
 *
 * A stored record keeps the catalogue — each series' name, unit, range and canonical id — that it
 * was imported with, and `DataLogManagerImpl.load` rewrites it only when the parser's version has
 * moved past the record's. So a change to what `parse` emits that leaves the number alone reaches
 * the charts and never reaches the sidebar beside them.
 *
 * That has now happened twice, once per format: the G1000 percent scaling and the SkyView percent
 * label both shipped without a bump, and both were reported as the fix not working. Hence one list
 * rather than one test per parser — a fourth format inherits the guard by being added here.
 *
 * The numbers are literals on purpose. Raising one fails this, and that failure is the question:
 * does `parse` now emit something different? If yes, raise both. If no, raise only this line.
 */
class ParserVersionsTest {

  @Test
  fun everyParserVersionIsWhatThisListSays() {
    val versions = mapOf(
      "garmin" to GarminParser().version,
      "dynon" to DynonParser().version,
      "avidyne" to AvidyneParser().version,
    )

    assertThat(versions).containsExactlyEntriesIn(
      mapOf(
        "garmin" to 3,
        "dynon" to 2,
        "avidyne" to 1,
      )
    )
  }
}
