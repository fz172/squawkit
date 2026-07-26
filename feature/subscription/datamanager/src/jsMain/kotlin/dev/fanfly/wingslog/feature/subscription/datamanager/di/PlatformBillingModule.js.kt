package dev.fanfly.wingslog.feature.subscription.datamanager.di

import dev.fanfly.wingslog.feature.subscription.model.BillingManager
import dev.fanfly.wingslog.feature.subscription.model.UnsupportedBillingManager
import org.koin.dsl.module

/**
 * Web cannot start a purchase — deliberately, and not only because RevenueCat ships no JS SDK.
 *
 * It still consumes a subscription bought on Android or iOS: entitlement is account-scoped and
 * server-authoritative, so `subscriptions/{uid}` syncs to the browser exactly as it does to a phone
 * and `SubscriptionManager` unlocks Pro here with no billing code involved. The web UI reads
 * `BillingManager.isPurchaseSupported == false` and points the pilot at the mobile app to subscribe
 * or manage their plan.
 */
actual val platformBillingModule = module {
  single<BillingManager> { UnsupportedBillingManager }
}
