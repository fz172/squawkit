package dev.fanfly.wingslog.core.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HydrationStateTest {

  @Test
  fun inProgress_completedLessThanTotal_isAccepted() {
    val state = HydrationState.InProgress(completed = 3, total = 6)
    assertThat(state.completed).isEqualTo(3)
    assertThat(state.total).isEqualTo(6)
  }

  @Test
  fun inProgress_completedEqualsTotal_isAccepted() {
    HydrationState.InProgress(completed = 6, total = 6)
  }

  @Test
  fun inProgress_completedZero_isAccepted() {
    HydrationState.InProgress(completed = 0, total = 6)
  }

  @Test(expected = IllegalArgumentException::class)
  fun inProgress_zeroTotal_rejected() {
    HydrationState.InProgress(completed = 0, total = 0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun inProgress_negativeCompleted_rejected() {
    HydrationState.InProgress(completed = -1, total = 6)
  }

  @Test(expected = IllegalArgumentException::class)
  fun inProgress_completedExceedsTotal_rejected() {
    HydrationState.InProgress(completed = 7, total = 6)
  }
}
