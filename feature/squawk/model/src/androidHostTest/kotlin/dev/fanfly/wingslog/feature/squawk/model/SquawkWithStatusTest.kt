package dev.fanfly.wingslog.feature.squawk.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.aircraft.Squawk
import dev.fanfly.wingslog.aircraft.SquawkDismissReason
import org.junit.Test

class SquawkWithStatusTest {

  // ---- OPEN ----

  @Test
  fun toWithStatus_emptyAddressedByLogId_andUnknownDismissReason_yieldsOpen() {
    val squawk = buildTestSquawk(
      addressedByLogId = "",
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
    )

    val result = squawk.toWithStatus()

    assertThat(result.status).isEqualTo(SquawkStatus.OPEN)
  }

  // ---- ADDRESSED ----

  @Test
  fun toWithStatus_nonEmptyAddressedByLogId_andUnknownDismissReason_yieldsAddressed() {
    val squawk = buildTestSquawk(
      addressedByLogId = "log-abc",
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
    )

    val result = squawk.toWithStatus()

    assertThat(result.status).isEqualTo(SquawkStatus.ADDRESSED)
  }

  // ---- DISMISSED ----

  @Test
  fun toWithStatus_nonUnknownDismissReason_andEmptyAddressedByLogId_yieldsDismissed() {
    val squawk = buildTestSquawk(
      addressedByLogId = "",
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
    )

    val result = squawk.toWithStatus()

    assertThat(result.status).isEqualTo(SquawkStatus.DISMISSED)
  }

  @Test
  fun toWithStatus_notReproducibleDismissReason_yieldsDismissed() {
    val squawk = buildTestSquawk(
      addressedByLogId = "",
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_NOT_REPRODUCIBLE,
    )

    val result = squawk.toWithStatus()

    assertThat(result.status).isEqualTo(SquawkStatus.DISMISSED)
  }

  @Test
  fun toWithStatus_duplicateDismissReason_yieldsDismissed() {
    val squawk = buildTestSquawk(
      addressedByLogId = "",
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_DUPLICATE,
    )

    val result = squawk.toWithStatus()

    assertThat(result.status).isEqualTo(SquawkStatus.DISMISSED)
  }

  // ---- ADDRESSED trumps DISMISSED ----

  @Test
  fun toWithStatus_nonEmptyAddressedByLogId_andNonUnknownDismissReason_yieldsAddressed() {
    val squawk = buildTestSquawk(
      addressedByLogId = "log-xyz",
      dismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_OBSOLETE,
    )

    val result = squawk.toWithStatus()

    assertThat(result.status).isEqualTo(SquawkStatus.ADDRESSED)
  }

  // ---- squawk field is preserved as-is ----

  @Test
  fun toWithStatus_preservesOriginalSquawkReference() {
    val squawk = buildTestSquawk(id = "sq-001")

    val result = squawk.toWithStatus()

    assertThat(result.squawk).isSameInstanceAs(squawk)
  }

  // ---- helpers ----

  private fun buildTestSquawk(
    id: String = "squawk-test-001",
    title: String = "Test squawk",
    addressedByLogId: String = "",
    dismissReason: SquawkDismissReason = SquawkDismissReason.SQUAWK_DISMISS_REASON_UNKNOWN,
  ): Squawk = Squawk(
    id = id,
    title = title,
    addressed_by_log_id = addressedByLogId,
    dismiss_reason = dismissReason,
  )
}
