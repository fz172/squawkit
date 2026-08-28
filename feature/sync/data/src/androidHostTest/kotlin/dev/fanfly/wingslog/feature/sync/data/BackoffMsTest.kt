package dev.fanfly.wingslog.feature.sync.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackoffMsTest {

  @Test
  fun backoffMs_withZeroAttempts_returnsZero() {
    assertThat(backoffMs(0)).isEqualTo(0L)
  }

  @Test
  fun backoffMs_withNegativeAttempts_returnsZero() {
    assertThat(backoffMs(-5)).isEqualTo(0L)
  }

  @Test
  fun backoffMs_withOneAttempt_returns60Seconds() {
    assertThat(backoffMs(1)).isEqualTo(60_000L)
  }

  @Test
  fun backoffMs_withTwoAttempts_returns120Seconds() {
    assertThat(backoffMs(2)).isEqualTo(120_000L)
  }

  @Test
  fun backoffMs_withThreeAttempts_returns240Seconds() {
    assertThat(backoffMs(3)).isEqualTo(240_000L)
  }

  @Test
  fun backoffMs_withSixAttempts_returnsCappedAt30Minutes() {
    // 30 × 2^6 = 30 × 64 = 1920s, capped at 1800s (30 min)
    assertThat(backoffMs(6)).isEqualTo(1_800_000L)
  }

  @Test
  fun backoffMs_withLargeAttemptCount_returnsCappedAt30Minutes() {
    assertThat(backoffMs(100)).isEqualTo(1_800_000L)
  }
}
