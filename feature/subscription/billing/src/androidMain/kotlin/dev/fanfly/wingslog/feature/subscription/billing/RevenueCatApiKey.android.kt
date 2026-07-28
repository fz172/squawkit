package dev.fanfly.wingslog.feature.subscription.billing

/**
 * Play Store production key (`goog_…`), from RevenueCat → Project settings → API keys → Google Play.
 *
 * Safe to keep in source for the same reason as the Test Store key: RevenueCat public SDK keys are
 * designed to be embedded in the client and can only read offerings and post receipts.
 *
 * Only a *release* build ever configures with this — developer and dogfood builds keep using the
 * Test Store key. See [RevenueCatApiKey] for why the two are not interchangeable.
 */
internal actual val platformProductionApiKey: String? = "goog_yzPMCqJgkvCMWogDsSDrgLwSBak"
