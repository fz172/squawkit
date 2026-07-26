package dev.fanfly.wingslog.feature.subscription.billing

/**
 * App Store production key (`appl_…`), from RevenueCat → Project settings → API keys → App Store.
 *
 * TODO(#GA): still `null` — the App Store Connect subscription products ("monthly", "yearly") are
 *  not set up yet. See the Android actual for why `null` is a safe shipping state.
 */
internal actual val platformProductionApiKey: String? = null
