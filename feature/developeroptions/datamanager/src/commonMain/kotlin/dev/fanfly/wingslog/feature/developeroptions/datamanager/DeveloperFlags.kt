package dev.fanfly.wingslog.feature.developeroptions.datamanager

import dev.fanfly.wingslog.core.model.settings.Subscription

data class DeveloperFlags(
  /**
   * Developer force-override of the effective subscription tier; `null` = no override (the account's
   * real entitlement applies). Honored only in developer builds — see SubscriptionManager.
   */
  val forceSubscriptionStatus: Subscription.Status? = null,
  /**
   * Developer force-override that shows display ads regardless of tier, so placement can be
   * exercised without a real free account. Honored only in developer builds, and only where the
   * build supports ads at all — it overrides the tier check, never the capability gate. See
   * AdsManagerImpl.
   */
  val forceAds: Boolean = false,
)
