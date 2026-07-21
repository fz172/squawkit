package dev.fanfly.wingslog.feature.subscription.model

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.settings.Subscription
import org.junit.Test

class SubscriptionResolutionTest {

  private val now = 1_700_000_000_000L
  private val later = now + 100_000L
  private val earlier = now - 100_000L

  private fun pro(
    lifecycle: Subscription.Lifecycle,
    periodEnd: Long = 0L,
  ) = Subscription(
    status = Subscription.Status.STATUS_PRO,
    lifecycle = lifecycle,
    current_period_end_millis = periodEnd,
  )

  @Test
  fun `active trialing and grace are PRO`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_ACTIVE).effectiveStatusAt(now))
      .isEqualTo(Subscription.Status.STATUS_PRO)
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_TRIALING).effectiveStatusAt(now))
      .isEqualTo(Subscription.Status.STATUS_PRO)
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_GRACE).effectiveStatusAt(now))
      .isEqualTo(Subscription.Status.STATUS_PRO)
  }

  @Test
  fun `canceled before period end is still PRO`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_CANCELED, periodEnd = later).effectiveStatusAt(now))
      .isEqualTo(Subscription.Status.STATUS_PRO)
  }

  @Test
  fun `canceled after period end lapses to FREE`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_CANCELED, periodEnd = earlier).effectiveStatusAt(now))
      .isEqualTo(Subscription.Status.STATUS_FREE)
  }

  @Test
  fun `canceled with no known period end is FREE`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_CANCELED, periodEnd = 0L).effectiveStatusAt(now))
      .isEqualTo(Subscription.Status.STATUS_FREE)
  }

  @Test
  fun `none and expired are FREE`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_NONE).effectiveStatusAt(now))
      .isEqualTo(Subscription.Status.STATUS_FREE)
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_EXPIRED).effectiveStatusAt(now))
      .isEqualTo(Subscription.Status.STATUS_FREE)
  }

  @Test
  fun `a default proto resolves FREE`() {
    assertThat(Subscription().effectiveStatusAt(now)).isEqualTo(Subscription.Status.STATUS_FREE)
  }
}
