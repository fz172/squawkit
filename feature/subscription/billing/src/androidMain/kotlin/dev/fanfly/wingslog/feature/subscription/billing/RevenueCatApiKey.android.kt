package dev.fanfly.wingslog.feature.subscription.billing

/**
 * Play Store production key (`goog_…`), from RevenueCat → Project settings → API keys → Google Play.
 *
 * TODO(#GA): still `null` — the Play Console subscription products ("monthly", "yearly") are not set
 *  up yet, so there is no production key to paste. While this is `null` a *release* build simply
 *  runs with purchasing unsupported, which is correct: `AppCapability.isSubscriptionSupported` is
 *  also `false` in the shipping release until GA. Developer and dogfood builds are unaffected —
 *  they use the Test Store key and can exercise the whole purchase flow today.
 */
internal actual val platformProductionApiKey: String? = null
