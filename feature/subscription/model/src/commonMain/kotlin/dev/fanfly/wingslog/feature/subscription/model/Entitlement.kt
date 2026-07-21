package dev.fanfly.wingslog.feature.subscription.model

import kotlin.time.Instant

/** Billing lifecycle of an entitlement. Resolves (with the period end) to an effective tier. */
enum class SubscriptionLifecycle {
  NONE,
  TRIALING,
  ACTIVE,
  CANCELED,
  GRACE,
  EXPIRED,
}

/** How the entitlement was granted. */
enum class EntitlementSource {
  UNSPECIFIED,
  STORE_PURCHASE,
  SERVER_GRANT,
}

/**
 * The account's subscription entitlement, as the client sees it — a clean domain model decoupled
 * from the `Subscription` proto the sync layer stores. The manager builds it from the local cache
 * and reduces it to an effective [SubscriptionStatus] via [effectiveStatusAt].
 *
 * See docs/subscription/subscription_design.html §4/§6.
 */
data class Entitlement(
  /** The tier the server granted (e.g. PRO). Whether it is *currently in effect* is [effectiveStatusAt]. */
  val grantedStatus: SubscriptionStatus,
  val lifecycle: SubscriptionLifecycle,
  val memberSince: Instant?,
  val currentPeriodEnd: Instant?,
  val willRenew: Boolean,
  val source: EntitlementSource,
  val originPlatform: String,
  val storageBytesUsed: Long,
  /** Per-tier cap; 0 = unlimited. */
  val storageQuotaBytes: Long,
) {

  /**
   * The tier actually in effect at [now]. Trials, active, and grace-period subscriptions entitle the
   * granted tier; a canceled subscription keeps it until [currentPeriodEnd] then lapses to FREE;
   * none/expired are FREE.
   */
  fun effectiveStatusAt(now: Instant): SubscriptionStatus = when (lifecycle) {
    SubscriptionLifecycle.TRIALING,
    SubscriptionLifecycle.ACTIVE,
    SubscriptionLifecycle.GRACE,
    -> grantedStatus

    SubscriptionLifecycle.CANCELED ->
      if (currentPeriodEnd != null && now < currentPeriodEnd) grantedStatus else SubscriptionStatus.FREE

    SubscriptionLifecycle.NONE,
    SubscriptionLifecycle.EXPIRED,
    -> SubscriptionStatus.FREE
  }

  companion object {
    /** The absence of any entitlement — a free account. */
    val FREE = Entitlement(
      grantedStatus = SubscriptionStatus.FREE,
      lifecycle = SubscriptionLifecycle.NONE,
      memberSince = null,
      currentPeriodEnd = null,
      willRenew = false,
      source = EntitlementSource.UNSPECIFIED,
      originPlatform = "",
      storageBytesUsed = 0L,
      storageQuotaBytes = 0L,
    )
  }
}
