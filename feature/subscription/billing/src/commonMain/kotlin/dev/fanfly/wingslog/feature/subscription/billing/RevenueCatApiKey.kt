package dev.fanfly.wingslog.feature.subscription.billing

/**
 * Which RevenueCat API key this build configures the SDK with.
 *
 * RevenueCat has two very different kinds of public SDK key, and picking the wrong one is a crash,
 * not a misconfiguration:
 *
 * - **Test Store key** (`test_…`) — cross-platform, backed by RevenueCat's simulated store. Purchases
 *   behave like real ones (they update `CustomerInfo`, fire entitlements and webhooks) without any
 *   App Store Connect / Play Console product setup — useful for developer/dogfood builds that don't
 *   want real store products. Subscriptions renew on an accelerated clock and expire after 5
 *   renewals.
 * - **Production keys** (`goog_…` for Play, `appl_…` for the App Store) — platform-specific, and the
 *   only keys allowed in a shipping build.
 *
 * The two are not interchangeable. **The RevenueCat SDK deliberately crashes a release build that is
 * configured with a Test Store key**, so the selection below is keyed on `isDeveloperBuild` rather
 * than on a flag someone could flip by accident.
 */
object RevenueCatApiKey {

  /**
   * RevenueCat Test Store key — developer and dogfood builds only.
   *
   * Safe to keep in source: RevenueCat public SDK keys are designed to be embedded in the client
   * (they can only read offerings and post receipts, never mutate account data), and this one cannot
   * reach real money at all. The *secret* half of this integration — the webhook authorization
   * header — lives in Cloud Functions config, never here.
   */
  private const val TEST_STORE_KEY = "test_cyjrLvZCJaTnceyCXOItRlYHWSE"

  /**
   * The platform's production key, or `null` until that store's products are live. Set on both
   * Android and iOS.
   *
   * `null` is a supported state, not a bug: [dev.fanfly.wingslog.feature.subscription.billing.impl]
   * skips `Purchases.configure` entirely and the app runs with purchasing unsupported — the state a
   * new platform starts in before its store products go live.
   */
  private val productionKey: String? = platformProductionApiKey

  /**
   * The key to configure with, or `null` to leave the SDK unconfigured (purchasing unsupported).
   *
   * @param isDeveloperBuild debug, dogfood, or any build that is not the shipping release. Sourced
   *   from the same signal as [dev.fanfly.wingslog.core.appinfo.AppCapability].
   */
  fun resolve(isDeveloperBuild: Boolean): String? =
    if (isDeveloperBuild) TEST_STORE_KEY else productionKey
}

/**
 * The production RevenueCat key for this platform: `goog_…` on Android, `appl_…` on iOS.
 *
 * Kept as an `expect val` because RevenueCat issues one key per store — there is no single
 * production key that works on both, and using the Android key on iOS fails at configure time.
 */
internal expect val platformProductionApiKey: String?
