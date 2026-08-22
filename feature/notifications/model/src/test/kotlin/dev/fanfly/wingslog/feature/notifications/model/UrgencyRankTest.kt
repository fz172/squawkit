package dev.fanfly.wingslog.feature.notifications.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.aircraft.SquawkPriority
import dev.fanfly.wingslog.feature.squawk.model.toWithStatus
import dev.fanfly.wingslog.feature.tasks.model.DueStatus
import org.junit.Test

class UrgencyRankTest {

  // DueStatus ladder — the whole point of this type is that ordinal order is NOT urgency order.

  @Test
  fun complied_ranksBelowDueSoon_despiteHigherOrdinal() {
    // DueStatus declares NORMAL, DUE_SOON, OVERDUE, COMPLIED — COMPLIED.ordinal == 3, the highest.
    assertThat(DueStatus.COMPLIED.ordinal).isGreaterThan(DueStatus.DUE_SOON.ordinal)

    assertThat(DueStatus.COMPLIED.urgencyRank()).isLessThan(DueStatus.DUE_SOON.urgencyRank())
  }

  @Test
  fun dueStatus_normalAndComplied_bothRankResolved() {
    assertThat(DueStatus.NORMAL.urgencyRank()).isEqualTo(UrgencyRank.RESOLVED)
    assertThat(DueStatus.COMPLIED.urgencyRank()).isEqualTo(UrgencyRank.RESOLVED)
  }

  @Test
  fun dueStatus_dueSoon_ranksAboveResolved() {
    assertThat(DueStatus.DUE_SOON.urgencyRank()).isGreaterThan(UrgencyRank.RESOLVED)
  }

  @Test
  fun dueStatus_overdue_ranksAboveDueSoon() {
    assertThat(DueStatus.OVERDUE.urgencyRank()).isGreaterThan(DueStatus.DUE_SOON.urgencyRank())
  }

  // Squawk ladder — every SquawkStatus x SquawkPriority combination.

  @Test
  fun squawk_addressed_ranksResolved_regardlessOfPriority() {
    val squawk = buildSquawk(priority = SquawkPriority.SQUAWK_PRIORITY_AOG, addressedByLogId = "log-1")
    assertThat(squawk.toWithStatus().urgencyRank()).isEqualTo(UrgencyRank.RESOLVED)
  }

  @Test
  fun squawk_dismissed_ranksResolved_regardlessOfPriority() {
    val squawk = buildSquawk(
      priority = SquawkPriority.SQUAWK_PRIORITY_HIGH,
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
    )
    assertThat(squawk.toWithStatus().urgencyRank()).isEqualTo(UrgencyRank.RESOLVED)
  }

  @Test
  fun squawk_open_isNeverRankResolved() {
    // An open squawk is never rank 0 — rank 0 means resolved, and an unset priority is still an
    // open defect.
    val squawk = buildSquawk(priority = SquawkPriority.SQUAWK_PRIORITY_UNKNOWN)
    assertThat(squawk.toWithStatus().urgencyRank()).isGreaterThan(UrgencyRank.RESOLVED)
  }

  @Test
  fun squawk_openUnknownPriority_ranksSameAsLow() {
    val unknown = buildSquawk(priority = SquawkPriority.SQUAWK_PRIORITY_UNKNOWN)
    val low = buildSquawk(priority = SquawkPriority.SQUAWK_PRIORITY_LOW)
    assertThat(unknown.toWithStatus().urgencyRank()).isEqualTo(low.toWithStatus().urgencyRank())
  }

  @Test
  fun squawk_openPriorityLadder_isStrictlyIncreasing() {
    val ranks = listOf(
      SquawkPriority.SQUAWK_PRIORITY_LOW,
      SquawkPriority.SQUAWK_PRIORITY_MEDIUM,
      SquawkPriority.SQUAWK_PRIORITY_HIGH,
      SquawkPriority.SQUAWK_PRIORITY_AOG,
    ).map { buildSquawk(priority = it).toWithStatus().urgencyRank() }

    for (i in 0 until ranks.lastIndex) {
      assertThat(ranks[i]).isLessThan(ranks[i + 1])
    }
  }

  @Test
  fun squawk_reopenFromDismissed_isAnEscalationFromResolved() {
    // PRD §6.1: a reopened squawk (Addressed/Dismissed -> Open) is treated as an escalation from
    // "resolved" at its stored priority. No special-casing needed in the mapping itself — this test
    // is the check that the mapping already produces that behavior via the plain rank > watermark
    // comparison a scanner would do.
    val dismissed = buildSquawk(
      priority = SquawkPriority.SQUAWK_PRIORITY_MEDIUM,
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
    )
    val reopened = buildSquawk(priority = SquawkPriority.SQUAWK_PRIORITY_MEDIUM)

    assertThat(dismissed.toWithStatus().urgencyRank()).isEqualTo(UrgencyRank.RESOLVED)
    assertThat(reopened.toWithStatus().urgencyRank()).isGreaterThan(dismissed.toWithStatus().urgencyRank())
  }

  private fun buildSquawk(
    priority: SquawkPriority,
    addressedByLogId: String = "",
    dismissReason: SquawkDismissReason =
      SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
  ): Squawk = Squawk(
    id = "squawk-test-001",
    title = "Test squawk",
    priority = priority,
    addressed_by_log_id = addressedByLogId,
    dismiss_reason = dismissReason,
  )
}
