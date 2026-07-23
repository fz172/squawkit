package dev.fanfly.wingslog.feature.sync.data

import com.google.common.truth.Truth.assertThat
import dev.fanfly.wingslog.core.model.settings.Subscription
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import org.junit.Test

class SubscriptionDocWireTest {

  @Test
  fun `toProto populates every field the Subscription proto declares`() {
    // The guard against silent proto→wire drift. A wire doc with every field a DISTINCT non-default
    // value; if the proto later gains a field SubscriptionDocWire doesn't map, toProto leaves it at
    // its default and this test names it — instead of the client silently reading a 0/"" as truth.
    // (See docs/subscription/subscription_design.html §3 for why this doc is hand-mirrored, not
    // stored as an opaque proto payload.)
    val fromFullWire = SubscriptionDocWire(
      status = 1, // STATUS_PRO (default STATUS_FREE)
      lifecycle = 2, // LIFECYCLE_ACTIVE (default LIFECYCLE_NONE)
      memberSinceMillis = 1_700_000_000_000,
      currentPeriodEndMillis = 1_702_000_000_000,
      willRenew = true, // default false
      source = 1, // SOURCE_STORE_PURCHASE (default SOURCE_UNSPECIFIED)
      originPlatform = "ios", // default ""
      storageBytesUsed = 240_000_000, // default 0
      storageQuotaBytes = 5, // default 0
    ).toProto()
    val allDefaults = Subscription()

    // The proto's schema fields are its primary-constructor params; Wire's `unknownFields` is the
    // extension-bytes sidecar, not a schema field. A field still equal to the all-defaults proto was
    // never populated by the wire doc — i.e. the wire doc doesn't map it.
    val schemaFieldNames = Subscription::class.primaryConstructor!!.parameters
      .mapNotNull { it.name }
      .filter { it != "unknownFields" }
      .toSet()
    val props = Subscription::class.memberProperties.filter { it.name in schemaFieldNames }
    // Prove the reflection actually resolved the schema, so an empty result can't false-pass below.
    assertThat(schemaFieldNames).isNotEmpty()
    assertThat(props).hasSize(schemaFieldNames.size)

    val unmapped = props
      .filter { it.get(fromFullWire) == it.get(allDefaults) }
      .map { it.name }

    assertThat(unmapped).isEmpty()
  }

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
