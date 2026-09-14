package dev.fanfly.wingslog.feature.datalog.datamanager.garmin

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.feature.datalog.datamanager.Fixtures
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.time.measureTime

/** PRD R8: a 20,000-row file (about 5.5 hours at 1 Hz) parses in well under the 3 s budget. */
class GarminParserPerformanceTest {

  @Test
  fun twentyThousandRowsParseWithinBudget() {
    val columns = (1..30).map { Triple("Series $it", "unit", "S$it") } +
      listOf(
        Triple("Latitude", "deg", "Latitude"),
        Triple("Longitude", "deg", "Longitude")
      )
    val rows = Fixtures.syntheticRows(20_000) { i ->
      (1..30).map { c -> if ((i + c) % 7 == 0) "" else "${(i * c) % 1000}.${c}" } +
        listOf(
          "+37.%07d".format(i % 1_000_000),
          "-121.%07d".format(i % 1_000_000)
        )
    }
    val bytes = Fixtures.synthetic(columns, rows)
    val parser = GarminParser()

    // Warm the JIT once; the measured pass is the second.
    runBlocking { parser.parse(bytes, "warm.csv") }
    val elapsed =
      measureTime { runBlocking { parser.parse(bytes, "timed.csv") } }
    val parsed = runBlocking { parser.parse(bytes, "check.csv") }

    assertThat(parsed.sampleCount).isEqualTo(20_000)
    assertThat(parsed.series).hasSize(31)
    assertThat(elapsed.inWholeMilliseconds).isLessThan(3_000)
  }
}
