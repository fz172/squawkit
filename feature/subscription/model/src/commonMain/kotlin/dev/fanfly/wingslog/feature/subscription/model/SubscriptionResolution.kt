package dev.fanfly.wingslog.feature.subscription.model

import dev.fanfly.wingslog.core.model.settings.Subscription

/**
 * Subscription domain logic over the `Subscription` proto — the single source of truth shared by the
 * client, the Cloud Functions (generated TS), and the Firestore doc, so nothing here re-declares the
 * status/lifecycle/source enums. See docs/subscription/subscription_design.html §4/§6.
 *
 * The proto's `status` is the tier the server *granted*; [effectiveStatusAt] decides whether that
 * tier is currently in effect given the billing lifecycle and the period end.
 */

/**
 * The tier actually in effect at [nowMillis] (epoch ms). Trials, active, and grace-period
 * subscriptions entitle the granted tier; a canceled subscription keeps it until
 * `current_period_end_millis`, then lapses to FREE; none/expired are FREE.
 */
fun Subscription.effectiveStatusAt(nowMillis: Long): Subscription.Status = when (lifecycle) {
  Subscription.Lifecycle.LIFECYCLE_TRIALING,
  Subscription.Lifecycle.LIFECYCLE_ACTIVE,
  Subscription.Lifecycle.LIFECYCLE_GRACE,
  -> status

  Subscription.Lifecycle.LIFECYCLE_CANCELED ->
    if (current_period_end_millis > nowMillis) status else Subscription.Status.STATUS_FREE

  Subscription.Lifecycle.LIFECYCLE_NONE,
  Subscription.Lifecycle.LIFECYCLE_EXPIRED,
  -> Subscription.Status.STATUS_FREE
}
