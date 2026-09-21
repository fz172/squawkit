package dev.fanfly.wingslog.feature.datalog.viewing.list

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.id.DataLogId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Test

class DataLogMonthsTest {

  private fun row(id: String, start: String) = DataLogRow(
    id = DataLogId(id),
    startLocal = LocalDateTime.parse(start),
    airborne = false,
    startLocationIdent = "",
    durationSeconds = 60,
    product = "",
    seriesCount = 1,
    identityMismatch = false,
    fileName = "",
    identity = "",
  )

  private val sep03 = row("a", "2026-09-03T10:06:00")
  private val sep02 = row("b", "2026-09-02T14:47:00")
  private val aug28 = row("c", "2026-08-28T09:12:00")
  private val aug2024 = row("d", "2024-08-10T10:46:00")

  @Test
  fun groupsConsecutiveRowsUnderTheirMonthInOrder() {
    val months = listOf(sep03, sep02, aug28, aug2024).byMonth()

    assertThat(months.map { it.month }).containsExactly(
      LocalDate(2026, 9, 1),
      LocalDate(2026, 8, 1),
      LocalDate(2024, 8, 1),
    )
      .inOrder()
    assertThat(months.first().rows).containsExactly(sep03, sep02)
      .inOrder()
  }

  @Test
  fun theSameMonthOfAnotherYearIsItsOwnGroup() {
    assertThat(listOf(aug28, aug2024).byMonth()).hasSize(2)
  }

  @Test
  fun keysAreUnique() {
    assertThat(
      listOf(sep03, aug28, sep02).byMonth()
        .map { it.key }).containsNoDuplicates()
  }

  @Test
  fun headlineLeadsWithDurationThenWhereThenGroundRun() {
    val ground = sep03.copy(durationSeconds = 1033, startLocationIdent = "E16")
    assertThat(ground.headline("Ground run")).isEqualTo("17m 13s · E16 · Ground run")
    assertThat(
      ground.copy(startLocationIdent = "")
        .headline("Ground run")
    )
      .isEqualTo("17m 13s · Ground run")
    assertThat(
      ground.copy(airborne = true)
        .headline("Ground run")
    ).isEqualTo("17m 13s · E16")
  }

  @Test
  fun startedTextIsDayAndClock() {
    assertThat(sep03.startedText()).isEqualTo("Sep 3, 10:06")
  }
}
