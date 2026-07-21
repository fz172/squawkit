package dev.fanfly.wingslog.feature.sync.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.settings.Subscription
import org.junit.Test

class SubscriptionDocWireTest {

  @Test
  fun `maps every field to the proto`() {
    val proto = SubscriptionDocWire(
      status = 1, // STATUS_PRO
      lifecycle = 2, // LIFECYCLE_ACTIVE
      memberSinceMillis = 1_700_000_000_000,
      currentPeriodEndMillis = 1_702_000_000_000,
      willRenew = true,
      source = 1, // SOURCE_STORE_PURCHASE
      originPlatform = "ios",
      storageBytesUsed = 240_000_000,
      storageQuotaBytes = 0,
    ).toProto()

    assertThat(proto.status).isEqualTo(Subscription.Status.STATUS_PRO)
    assertThat(proto.lifecycle).isEqualTo(Subscription.Lifecycle.LIFECYCLE_ACTIVE)
    assertThat(proto.member_since_millis).isEqualTo(1_700_000_000_000)
    assertThat(proto.current_period_end_millis).isEqualTo(1_702_000_000_000)
    assertThat(proto.will_renew).isTrue()
    assertThat(proto.source).isEqualTo(Subscription.Source.SOURCE_STORE_PURCHASE)
    assertThat(proto.origin_platform).isEqualTo("ios")
    assertThat(proto.storage_bytes_used).isEqualTo(240_000_000)
    assertThat(proto.storage_quota_bytes).isEqualTo(0)
  }

  @Test
  fun `an empty doc resolves to the free defaults`() {
    val proto = SubscriptionDocWire().toProto()

    assertThat(proto.status).isEqualTo(Subscription.Status.STATUS_FREE)
    assertThat(proto.lifecycle).isEqualTo(Subscription.Lifecycle.LIFECYCLE_NONE)
    assertThat(proto.source).isEqualTo(Subscription.Source.SOURCE_UNSPECIFIED)
    assertThat(proto.will_renew).isFalse()
  }

  @Test
  fun `an unknown enum value falls back to the zero default rather than throwing`() {
    val proto = SubscriptionDocWire(status = 99, lifecycle = 99, source = 99).toProto()

    assertThat(proto.status).isEqualTo(Subscription.Status.STATUS_FREE)
    assertThat(proto.lifecycle).isEqualTo(Subscription.Lifecycle.LIFECYCLE_NONE)
    assertThat(proto.source).isEqualTo(Subscription.Source.SOURCE_UNSPECIFIED)
  }
}
