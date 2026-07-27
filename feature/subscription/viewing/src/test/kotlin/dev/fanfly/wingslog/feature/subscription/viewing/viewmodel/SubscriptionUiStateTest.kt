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

  @Test
  fun `purchase platform names the store that billed`() {
    assertThat(purchasePlatformOf("app_store")).isEqualTo(PurchasePlatform.APP_STORE)
    assertThat(purchasePlatformOf("mac_app_store")).isEqualTo(PurchasePlatform.MAC_APP_STORE)
    assertThat(purchasePlatformOf("play_store")).isEqualTo(PurchasePlatform.PLAY_STORE)
    assertThat(purchasePlatformOf("test_store")).isEqualTo(PurchasePlatform.TEST_STORE)
    // Play and Amazon cancel in completely different places, so they must not collapse together.
    assertThat(purchasePlatformOf("amazon")).isEqualTo(PurchasePlatform.AMAZON)
    assertThat(purchasePlatformOf("amazon")).isNotEqualTo(purchasePlatformOf("play_store"))
  }

  @Test
  fun `the web billers collapse into one entry`() {
    assertThat(purchasePlatformOf("stripe")).isEqualTo(PurchasePlatform.WEB)
    assertThat(purchasePlatformOf("rc_billing")).isEqualTo(PurchasePlatform.WEB)
    assertThat(purchasePlatformOf("paddle")).isEqualTo(PurchasePlatform.WEB)
  }

  @Test
  fun `grants and unrecognised platforms show no row rather than the word unknown`() {
    // A comp has no store to cancel at, so naming one would be worse than saying nothing.
    assertThat(purchasePlatformOf("server")).isNull()
    assertThat(purchasePlatformOf("promotional")).isNull()
    assertThat(purchasePlatformOf("unknown")).isNull()
    assertThat(purchasePlatformOf("")).isNull()
    // A value written by a newer server than this client falls into the same silent bucket.
    assertThat(purchasePlatformOf("some_future_store")).isNull()
  }

  @Test
  fun `purchase platform is read from the entitlement`() {
    val state = toSubscriptionUiState(
      Subscription.Status.STATUS_PRO,
      Subscription(origin_platform = "play_store"),
      TimeZone.UTC,
    )
    assertThat(state.purchasePlatform).isEqualTo(PurchasePlatform.PLAY_STORE)
  }
}
