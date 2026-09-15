package dev.fanfly.wingslog.feature.search.model

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test

class TimeWindowTest {

  private val today = LocalDate(2026, 9, 6)

  @Test
  fun all_containsAnyDate() {
    assertThat(
      TimeWindow.All.contains(
        LocalDate(1999, 1, 1),
        today,
        TimeDirection.PAST
      )
    ).isTrue()
    assertThat(
      TimeWindow.All.contains(
        LocalDate(2099, 1, 1),
        today,
        TimeDirection.FUTURE
      )
    ).isTrue()
  }

  @Test
  fun lastMonths_past_isInclusiveAtBothEnds() {
    val window = TimeWindow.LastMonths(3)
    assertThat(
      window.contains(
        LocalDate(2026, 6, 6),
        today,
        TimeDirection.PAST
      )
    ).isTrue()
    assertThat(window.contains(today, today, TimeDirection.PAST)).isTrue()
    assertThat(
      window.contains(
        LocalDate(2026, 6, 5),
        today,
        TimeDirection.PAST
      )
    ).isFalse()
    assertThat(
      window.contains(
        LocalDate(2026, 9, 7),
        today,
        TimeDirection.PAST
      )
    ).isFalse()
  }

  @Test
  fun lastMonths_future_countsForwardFromToday() {
    val window = TimeWindow.LastMonths(3)
    assertThat(window.contains(today, today, TimeDirection.FUTURE)).isTrue()
    assertThat(
      window.contains(
        LocalDate(2026, 12, 6),
        today,
        TimeDirection.FUTURE
      )
    ).isTrue()
    assertThat(
      window.contains(
        LocalDate(2026, 12, 7),
        today,
        TimeDirection.FUTURE
      )
    ).isFalse()
    assertThat(
      window.contains(
        LocalDate(2026, 9, 5),
        today,
        TimeDirection.FUTURE
      )
    ).isFalse()
  }

  @Test
  fun lastMonths_clampsToEndOfShorterMonth() {
    val endOfMay = LocalDate(2026, 5, 31)
    val window = TimeWindow.LastMonths(3)
    // Three months before May 31 is the last day of February, not a March date.
    assertThat(
      window.contains(
        LocalDate(2026, 2, 28),
        endOfMay,
        TimeDirection.PAST
      )
    ).isTrue()
    assertThat(
      window.contains(
        LocalDate(2026, 2, 27),
        endOfMay,
        TimeDirection.PAST
      )
    ).isFalse()
  }

  @Test
  fun custom_isInclusiveAndIgnoresDirection() {
    val window =
      TimeWindow.Custom(LocalDate(2026, 1, 1), LocalDate(2026, 6, 30))
    assertThat(
      window.contains(
        LocalDate(2026, 1, 1),
        today,
        TimeDirection.PAST
      )
    ).isTrue()
    assertThat(
      window.contains(
        LocalDate(2026, 6, 30),
        today,
        TimeDirection.FUTURE
      )
    ).isTrue()
    assertThat(
      window.contains(
        LocalDate(2026, 7, 1),
        today,
        TimeDirection.PAST
      )
    ).isFalse()
    assertThat(
      window.contains(
        LocalDate(2025, 12, 31),
        today,
        TimeDirection.PAST
      )
    ).isFalse()
  }

  @Test
  fun tappingTheWindowAlreadyInForceClearsItBackToAll() {
    val threeMonths = TimeWindow.LastMonths(3)

    // A fresh option is selected; the same one again clears.
    assertThat(timeWindowAfterTap(TimeWindow.All, threeMonths)).isEqualTo(threeMonths)
    assertThat(timeWindowAfterTap(threeMonths, threeMonths)).isEqualTo(TimeWindow.All)

    // Switching between options still switches rather than clearing.
    assertThat(timeWindowAfterTap(threeMonths, TimeWindow.LastMonths(12)))
      .isEqualTo(TimeWindow.LastMonths(12))

    // "Any time" is the cleared state, so tapping it while selected is a no-op.
    assertThat(timeWindowAfterTap(TimeWindow.All, TimeWindow.All)).isEqualTo(TimeWindow.All)
  }

  @Test
  fun theCustomChipClearsWhateverRangeItHolds() {
    val current = TimeWindow.Custom(LocalDate(2026, 1, 1), LocalDate(2026, 3, 1))
    // The chip always offers a default range, so it never equals the range on screen.
    val tapped = TimeWindow.Custom(LocalDate(2026, 6, 15), LocalDate(2026, 9, 15))

    assertThat(timeWindowAfterTap(current, tapped)).isEqualTo(TimeWindow.All)
    assertThat(timeWindowAfterTap(TimeWindow.All, tapped)).isEqualTo(tapped)
    assertThat(timeWindowAfterTap(TimeWindow.LastMonths(3), tapped)).isEqualTo(tapped)
  }
}
