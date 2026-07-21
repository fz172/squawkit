package dev.fanfly.wingslog.feature.subscription.model

/**
 * The subscription tier the app gates features on.
 *
 * Ordered low → high so a feature can require a minimum tier with a simple comparison; extensible
 * with future paid tiers without disturbing existing gates. See
 * docs/subscription/subscription_design.html §4.
 */
enum class SubscriptionStatus {
  FREE,
  PRO,
}
