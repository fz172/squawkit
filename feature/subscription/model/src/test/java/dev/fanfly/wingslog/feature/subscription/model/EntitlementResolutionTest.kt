package dev.fanfly.wingslog.feature.subscription.model

import com.google.common.truth.Truth.assertThat
import kotlin.time.Instant
import org.junit.Test

class EntitlementResolutionTest {

  private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
  private val later = Instant.fromEpochMilliseconds(1_700_000_100_000)
  private val earlier = Instant.fromEpochMilliseconds(1_699_999_900_000)

  private fun pro(
    lifecycle: SubscriptionLifecycle,
    periodEnd: Instant? = null,
  ) = Entitlement.FREE.copy(
    grantedStatus = SubscriptionStatus.PRO,
    lifecycle = lifecycle,
    currentPeriodEnd = periodEnd,
  )

  @Test
  fun `active trialing and grace are PRO`() {
    assertThat(pro(SubscriptionLifecycle.ACTIVE).effectiveStatusAt(now)).isEqualTo(SubscriptionStatus.PRO)
    assertThat(pro(SubscriptionLifecycle.TRIALING).effectiveStatusAt(now)).isEqualTo(SubscriptionStatus.PRO)
    assertThat(pro(SubscriptionLifecycle.GRACE).effectiveStatusAt(now)).isEqualTo(SubscriptionStatus.PRO)
  }

  @Test
  fun `canceled before period end is still PRO`() {
    assertThat(pro(SubscriptionLifecycle.CANCELED, periodEnd = later).effectiveStatusAt(now))
      .isEqualTo(SubscriptionStatus.PRO)
  }

  @Test
  fun `canceled after period end lapses to FREE`() {
    assertThat(pro(SubscriptionLifecycle.CANCELED, periodEnd = earlier).effectiveStatusAt(now))
      .isEqualTo(SubscriptionStatus.FREE)
  }

  @Test
  fun `canceled with no known period end is FREE`() {
    assertThat(pro(SubscriptionLifecycle.CANCELED, periodEnd = null).effectiveStatusAt(now))
      .isEqualTo(SubscriptionStatus.FREE)
  }

  @Test
  fun `none and expired are FREE`() {
    assertThat(pro(SubscriptionLifecycle.NONE).effectiveStatusAt(now)).isEqualTo(SubscriptionStatus.FREE)
    assertThat(pro(SubscriptionLifecycle.EXPIRED).effectiveStatusAt(now)).isEqualTo(SubscriptionStatus.FREE)
  }

  @Test
  fun `the FREE constant resolves FREE`() {
    assertThat(Entitlement.FREE.effectiveStatusAt(now)).isEqualTo(SubscriptionStatus.FREE)
  }
}
