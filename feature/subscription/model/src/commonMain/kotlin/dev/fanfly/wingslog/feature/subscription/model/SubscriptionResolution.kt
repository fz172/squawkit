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
 * subscriptions entitle the granted tier *while something is still expected to renew them*; a
 * subscription that will not renew keeps the tier until `current_period_end_millis`, then lapses to
 * FREE; none/expired are FREE.
 *
 * ## Why `will_renew` decides whether the period end is binding
 *
 * When `will_renew` is true, the store is expected to bill again and send a renewal that pushes
 * `current_period_end_millis` forward. A period end that has just passed therefore means "the
 * renewal has not reached us *yet*", not "this lapsed" — the two are indistinguishable from here.
 * Expiring locally would flap every cycle on ordinary webhook latency, and would wrongly downgrade a
 * paying pilot who is simply offline, which is precisely when they cannot receive the renewal that
 * would keep them Pro. Offline-first means trusting the last synced entitlement. Bounding the other
 * side of that trust — an entitlement stuck ACTIVE because a renewal was genuinely missed — is the
 * server's job, since only it can ask the provider what really happened.
 *
 * When `will_renew` is false there is no such ambiguity: nothing further is coming, so the period end
 * is final and honoring it is simply correct. This covers one-off purchases (`NON_RENEWING_PURCHASE`)
 * and, importantly, server-granted comps — `grantPromoEntitlement` writes ACTIVE with
 * `willRenew: false` and an end date, and documents that it "lets it lapse back to Free on its own".
 * Before this, ACTIVE ignored the end date entirely and a comp never expired.
 */
fun Subscription.effectiveStatusAt(nowMillis: Long): Subscription.Status = when (lifecycle) {
  Subscription.Lifecycle.LIFECYCLE_TRIALING,
  Subscription.Lifecycle.LIFECYCLE_ACTIVE,
  Subscription.Lifecycle.LIFECYCLE_GRACE,
  -> if (will_renew) status else statusUntilPeriodEnd(nowMillis)

  Subscription.Lifecycle.LIFECYCLE_CANCELED -> statusUntilPeriodEnd(nowMillis)

  Subscription.Lifecycle.LIFECYCLE_NONE,
  Subscription.Lifecycle.LIFECYCLE_EXPIRED,
  -> Subscription.Status.STATUS_FREE
}

/**
 * The granted tier while `current_period_end_millis` is still in the future, else FREE.
 *
 * An unset end date (0) reads as lapsed rather than as "forever" — the conservative direction, and
 * the same way the canceled case has always treated it.
 */
private fun Subscription.statusUntilPeriodEnd(nowMillis: Long): Subscription.Status =
  if (current_period_end_millis > nowMillis) status else Subscription.Status.STATUS_FREE
