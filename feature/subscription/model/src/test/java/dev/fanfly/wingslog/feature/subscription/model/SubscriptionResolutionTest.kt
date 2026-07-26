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
    willRenew: Boolean = false,
  ) = Subscription(
    status = Subscription.Status.STATUS_PRO,
    lifecycle = lifecycle,
    current_period_end_millis = periodEnd,
    will_renew = willRenew,
  )

  private fun Subscription.resolve() = effectiveStatusAt(now)

  private val pro = Subscription.Status.STATUS_PRO
  private val free = Subscription.Status.STATUS_FREE

  // --- Renewing: the period end is not binding ------------------------------------------------

  @Test
  fun `renewing active trialing and grace are PRO`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_ACTIVE, later, willRenew = true).resolve())
      .isEqualTo(pro)
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_TRIALING, later, willRenew = true).resolve())
      .isEqualTo(pro)
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_GRACE, later, willRenew = true).resolve())
      .isEqualTo(pro)
  }

  @Test
  fun `renewing subscription stays PRO past its period end while the renewal is in flight`() {
    // Deliberate: a renewal is expected, so a just-passed period end means "not heard yet", not
    // "lapsed" — and the two are indistinguishable from the client. Bounding a renewal that never
    // arrives is the server's job; expiring here would flap every cycle and would downgrade a
    // paying pilot who is merely offline.
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_ACTIVE, earlier, willRenew = true).resolve())
      .isEqualTo(pro)
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_GRACE, earlier, willRenew = true).resolve())
      .isEqualTo(pro)
  }

  // --- Not renewing: the period end is final --------------------------------------------------

  @Test
  fun `non-renewing subscription is PRO until its period end`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_ACTIVE, later).resolve()).isEqualTo(pro)
  }

  @Test
  fun `non-renewing subscription lapses to FREE after its period end`() {
    // The server-granted comp case: grantPromoEntitlement writes ACTIVE + willRenew=false with an
    // end date, and documents that it lapses on its own. Before this it never expired.
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_ACTIVE, earlier).resolve()).isEqualTo(free)
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_TRIALING, earlier).resolve()).isEqualTo(free)
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_GRACE, earlier).resolve()).isEqualTo(free)
  }

  @Test
  fun `non-renewing subscription with no known period end is FREE`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_ACTIVE, periodEnd = 0L).resolve())
      .isEqualTo(free)
  }

  // --- Canceled: unchanged, the period end has always been binding ----------------------------

  @Test
  fun `canceled before period end is still PRO`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_CANCELED, periodEnd = later).resolve())
      .isEqualTo(pro)
  }

  @Test
  fun `canceled after period end lapses to FREE`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_CANCELED, periodEnd = earlier).resolve())
      .isEqualTo(free)
  }

  @Test
  fun `canceled with no known period end is FREE`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_CANCELED, periodEnd = 0L).resolve())
      .isEqualTo(free)
  }

  // --- Terminal states -------------------------------------------------------------------------

  @Test
  fun `none and expired are FREE`() {
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_NONE, later, willRenew = true).resolve())
      .isEqualTo(free)
    assertThat(pro(Subscription.Lifecycle.LIFECYCLE_EXPIRED, later, willRenew = true).resolve())
      .isEqualTo(free)
  }

  @Test
  fun `a default proto resolves FREE`() {
    assertThat(Subscription().effectiveStatusAt(now)).isEqualTo(free)
  }
}
