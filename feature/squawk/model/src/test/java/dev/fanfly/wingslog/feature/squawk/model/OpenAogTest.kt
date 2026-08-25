package dev.fanfly.wingslog.feature.squawk.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import dev.fanfly.wingslog.aircraft.SquawkPriority
import org.junit.Test

/**
 * The dashboard AOG alert. A squawk closes two ways — addressed by a maintenance log, or dismissed
 * with a reason — and both have to drop it off the banner.
 */
class OpenAogTest {

  @Test
  fun openAogSquawk_isReported() {
    val squawk = buildAogSquawk()

    assertThat(listOf(squawk.toWithStatus()).openAog()).containsExactly(squawk)
  }

  @Test
  fun addressedAogSquawk_isNotReported() {
    val squawk = buildAogSquawk(addressedByLogId = "log-1")

    assertThat(listOf(squawk.toWithStatus()).openAog()).isEmpty()
  }

  @Test
  fun dismissedAogSquawk_isNotReported() {
    // Dismissing leaves addressed_by_log_id empty, so a filter that only checks that field kept the
    // banner up on every device after the squawk had been closed.
    val squawk = buildAogSquawk(
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
    )

    assertThat(listOf(squawk.toWithStatus()).openAog()).isEmpty()
  }

  @Test
  fun openSquawkBelowAog_isNotReported() {
    val squawk = buildAogSquawk(priority = SquawkPriority.SQUAWK_PRIORITY_HIGH)

    assertThat(listOf(squawk.toWithStatus()).openAog()).isEmpty()
  }

  @Test
  fun onlyTheOpenAogSquawksSurvive_inOrder() {
    val open = buildAogSquawk(id = "open")
    val dismissed = buildAogSquawk(
      id = "dismissed",
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE,
    )
    val addressed = buildAogSquawk(id = "addressed", addressedByLogId = "log-1")
    val stillOpen = buildAogSquawk(id = "still-open")

    val result = listOf(open, dismissed, addressed, stillOpen).map { it.toWithStatus() }.openAog()

    assertThat(result).containsExactly(open, stillOpen).inOrder()
  }

  // ---- helpers ----

  private fun buildAogSquawk(
    id: String = "squawk-test-001",
    priority: SquawkPriority = SquawkPriority.SQUAWK_PRIORITY_AOG,
    addressedByLogId: String = "",
    dismissReason: SquawkDismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
  ): Squawk = Squawk(
    id = id,
    title = "Test squawk",
    priority = priority,
    addressed_by_log_id = addressedByLogId,
    dismiss_reason = dismissReason,
  )
}
