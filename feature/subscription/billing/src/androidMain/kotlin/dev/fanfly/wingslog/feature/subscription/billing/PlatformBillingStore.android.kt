package dev.fanfly.wingslog.feature.subscription.billing

import dev.fanfly.wingslog.feature.subscription.model.PurchasePlatform

/** SquawkIt ships to Google Play only; an Amazon Appstore build would need its own key and store. */
internal actual val platformBillingStore: PurchasePlatform = PurchasePlatform.PLAY_STORE
