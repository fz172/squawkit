package dev.fanfly.wingslog.feature.subscription.viewing.viewmodel

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.settings.Subscription
import kotlinx.datetime.TimeZone
import org.junit.Test

class SubscriptionUiStateTest {

  @Test
  fun `pro status maps to isPro with lifecycle and storage carried through`() {
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(
        status = Subscription.Status.STATUS_PRO,
        lifecycle = Subscription.Lifecycle.LIFECYCLE_ACTIVE,
        will_renew = true,
        storage_bytes_used = 240_000_000,
      ),
      TimeZone.UTC,
    )
    assertThat(state.isPro).isTrue()
    assertThat(state.lifecycle).isEqualTo(Subscription.Lifecycle.LIFECYCLE_ACTIVE)
    assertThat(state.willRenew).isTrue()
    assertThat(state.storageBytesUsed).isEqualTo(240_000_000)
  }

  @Test
  fun `free status is not pro`() {
    assertThat(toSubscriptionUiState(Subscription.Status.STATUS_FREE, Subscription(), TimeZone.UTC).isPro)
      .isFalse()
  }

  @Test
  fun `member since is null when unset and formatted when present`() {
    val unset = toSubscriptionUiState(
      Subscription.Status.STATUS_FREE,
      Subscription(member_since_millis = 0L),
      TimeZone.UTC,
    )
    assertThat(unset.memberSince).isNull()

    val present = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(member_since_millis = 1_700_000_000_000L),
      TimeZone.UTC,
    )
    // 2023-11-14 UTC.
    assertThat(present.memberSince).isEqualTo("Nov 14, 2023")
  }
}
