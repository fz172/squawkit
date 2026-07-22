package dev.fanfly.wingslog.feature.developeroptions.datamanager

import dev.fanfly.wingslog.core.model.settings.Subscription

data class DeveloperFlags(
  val attachmentUploadEnabled: Boolean = false,
  val exportEmailDeliveryEnabled: Boolean = false,
  /**
   * Developer force-override of the effective subscription tier; `null` = no override (the account's
   * real entitlement applies). Honored only in developer builds — see SubscriptionManager.
   */
  val forceSubscriptionStatus: Subscription.Status? = null,
)
