package dev.fanfly.wingslog.feature.logs.viewing.log.compose

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.datetime.toWireInstant
import dev.fanfly.wingslog.thing.MaintenanceLog
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Test

class LogListLinesTest {

  private fun log(id: String, date: String?) = MaintenanceLog(
    id = id,
    timestamp = date?.let { toWireInstant(Instant.parse("${it}T12:00:00Z").epochSeconds) },
  )

  private val sep17 = log("a", "2026-09-17")
  private val sep03 = log("b", "2026-09-03")
  private val jul25 = log("c", "2026-07-25")

  @Test
  fun headsEachMonthWithItsCount() {
    val lines = logListLines(listOf(sep17, sep03, jul25), showAds = false)

    assertThat(lines.filterIsInstance<LogListLine.MonthHeader>()).containsExactly(
      LogListLine.MonthHeader(LocalDate(2026, 9, 1), 2, firstLogId = "a"),
      LogListLine.MonthHeader(LocalDate(2026, 7, 1), 1, firstLogId = "c"),
    ).inOrder()
  }

  @Test
  fun spineConnectsNeighboursAndBreaksAtAHeader() {
    val entries = logListLines(listOf(sep17, sep03, jul25), showAds = false)
      .filterIsInstance<LogListLine.Entry>()

    assertThat(entries.map { it.connectsDown }).containsExactly(true, false, false).inOrder()
    assertThat(entries.map { it.connectsUp }).containsExactly(false, true, false).inOrder()
  }

  @Test
  fun onlyTheNewestLogIsLit() {
    val entries = logListLines(listOf(sep17, sep03), showAds = false)
      .filterIsInstance<LogListLine.Entry>()

    assertThat(entries.map { it.isLatest }).containsExactly(true, false).inOrder()
  }

  @Test
  fun anUndatedLogHeadsItsOwnGroup() {
    val lines = logListLines(listOf(sep17, log("u", null)), showAds = false)

    assertThat(lines[2]).isEqualTo(LogListLine.MonthHeader(month = null, count = 1, firstLogId = "u"))
  }

  @Test
  fun keepsTheIncomingOrder() {
    val lines = logListLines(listOf(jul25, sep17), showAds = false)

    assertThat(lines.filterIsInstance<LogListLine.Entry>().map { it.log.id })
      .containsExactly("c", "a").inOrder()
  }

  @Test
  fun keysAreUniqueWithAds() {
    val lines = logListLines(listOf(sep17, sep03, jul25), showAds = true)

    assertThat(lines.map { it.key }).containsNoDuplicates()
    assertThat(lines.filterIsInstance<LogListLine.Ad>()).hasSize(1)
  }

  @Test
  fun aFilteredListReportsWhatItSkippedBetweenMatches() {
    val lines = logListLines(listOf(sep17, jul25), showAds = false, allLogs = listOf(sep17, sep03, jul25))

    assertThat(lines).containsExactly(
      LogListLine.MonthHeader(LocalDate(2026, 9, 1), 1, firstLogId = "a"),
      LogListLine.Entry(sep17, connectsDown = true, isLatest = true),
      LogListLine.Gap(omitted = 1, afterLogId = "a"),
      LogListLine.MonthHeader(LocalDate(2026, 7, 1), 1, firstLogId = "c"),
      LogListLine.Entry(jul25),
    ).inOrder()
  }

  @Test
  fun nothingIsReportedBeforeTheFirstMatchOrAfterTheLast() {
    val lines = logListLines(listOf(sep03), showAds = false, allLogs = listOf(sep17, sep03, jul25))

    assertThat(lines.filterIsInstance<LogListLine.Gap>()).isEmpty()
  }

  @Test
  fun anUnfilteredListHasNoGaps() {
    val lines = logListLines(listOf(sep17, sep03, jul25), showAds = false)

    assertThat(lines.filterIsInstance<LogListLine.Gap>()).isEmpty()
  }

  @Test
  fun aMatchThatIsNotTheNewestLogIsNotLit() {
    val entries = logListLines(listOf(sep03), showAds = false, allLogs = listOf(sep17, sep03))
      .filterIsInstance<LogListLine.Entry>()

    assertThat(entries.single().isLatest).isFalse()
  }
}
