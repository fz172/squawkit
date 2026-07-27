package dev.fanfly.wingslog.feature.subscription.billing

import dev.fanfly.wingslog.feature.subscription.model.PurchasePlatform

/** Covers Mac Catalyst purchases too: both Apple storefronts are managed from the same place. */
internal actual val platformBillingStore: PurchasePlatform = PurchasePlatform.APP_STORE
