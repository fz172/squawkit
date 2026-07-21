package dev.fanfly.wingslog.feature.sync.data

import dev.fanfly.wingslog.core.model.settings.Subscription
import kotlinx.serialization.Serializable

/**
 * On-the-wire shape of the server-authoritative entitlement doc at top-level `subscriptions/{uid}`.
 *
 * Unlike the rest of sync, this doc is stored as **plain Firestore fields** (not an opaque proto
 * payload), so the two server writers — the billing pipeline and the storage-usage sweep — can
 * `set(..., merge)` their own fields independently without a read-modify-write race. The client
 * decodes it via GitLive's `snapshot.data<SubscriptionDocWire>()` and maps it to the [Subscription]
 * proto for the local store. See docs/subscription/subscription_design.html §3.
 *
 * Every field defaults, so a partially-written doc (e.g. only the sweep has run) decodes cleanly.
 * Enums travel as their proto int value; an unknown value falls back to the proto3 zero default.
 */
@Serializable
internal data class SubscriptionDocWire(
  val status: Int = 0,
  val lifecycle: Int = 0,
  val memberSinceMillis: Long = 0L,
  val currentPeriodEndMillis: Long = 0L,
  val willRenew: Boolean = false,
  val source: Int = 0,
  val originPlatform: String = "",
  val storageBytesUsed: Long = 0L,
  val storageQuotaBytes: Long = 0L,
)

internal fun SubscriptionDocWire.toProto(): Subscription = Subscription(
  status = Subscription.Status.fromValue(status) ?: Subscription.Status.STATUS_FREE,
  lifecycle = Subscription.Lifecycle.fromValue(lifecycle) ?: Subscription.Lifecycle.LIFECYCLE_NONE,
  member_since_millis = memberSinceMillis,
  current_period_end_millis = currentPeriodEndMillis,
  will_renew = willRenew,
  source = Subscription.Source.fromValue(source) ?: Subscription.Source.SOURCE_UNSPECIFIED,
  origin_platform = originPlatform,
  storage_bytes_used = storageBytesUsed,
  storage_quota_bytes = storageQuotaBytes,
)
