package dev.fanfly.wingslog.core.appinfo

/**
 * Single point of truth for what this build/platform/install supports. Constructed once per host
 * at Koin startup via [createAppCapability] and injected wherever a feature needs to gate on it.
 */
data class AppCapability(
  val isDeveloperOptionsSupported: Boolean,
  /**
   * Aircraft sharing (#134, design §6.1). The staged-rollout gate: on in dev and dogfood, off in
   * the shipping release, and GA is flipping this to `true`.
   *
   * A build-time gate rather than a Developer Options flag, because Developer Options only exists in developer
   * builds ([isDeveloperOptionsSupported] is `isDeveloperBuild`) — a lab toggle could never be turned on
   * in a release build, so it cannot express "ship this to real users later".
   *
   * "Off" means genuinely off, not merely hidden: entry points disappear AND an inbound invite link
   * is ignored rather than parked. A door that is hidden but still opens is not a gate.
   */
  val isAircraftSharingSupported: Boolean,
  val isStressTestSupported: Boolean,
  val isCameraCaptureSupported: Boolean,
  val isAnonymousLoginSupported: Boolean,
  val isAppleSignInSupported: Boolean,
  /**
   * Whether a guest can attach their session to a Google account on this platform.
   *
   * Separate from Google *sign-in*, which works everywhere: linking needs a credential, and iOS's
   * native provider signs in to Firebase itself rather than handing one back. Offering it there
   * would put a button in the picker that can never succeed.
   */
  val isGoogleUpgradeSupported: Boolean,
  /**
   * Subscriptions / SquawkIt Pro (subscription design §1). The staged-rollout gate, like
   * [isAircraftSharingSupported]: on in dev + dogfood, off in the shipping release until GA.
   *
   * A build-time gate rather than a Developer Options flag, because the whole paywall must be able to
   * ship dark and be turned on later. Crucially, **"off" means unlocked for everyone, not
   * restricted**: while this is false there is no gating at all — every premium feature is treated
   * as available and the Subscription entry/page is hidden. GA is flipping this to `true`.
   */
  val isSubscriptionSupported: Boolean,
  /**
   * Free-tier display ads (`docs/ads/ads_design.html` §6). The staged-rollout gate: on in dev +
   * dogfood, off in the shipping release until GA, and `false` on the web host for all of v1.
   *
   * **Read this before changing it: the polarity is the opposite of every flag above.**
   * [isSubscriptionSupported] and [isAircraftSharingSupported] are *default-open* — while they are
   * false there is no paywall and every premium capability reads available. This flag is
   * **default-closed**: while it is false, or while [isSubscriptionSupported] is false, there are
   * **no ads at all**.
   *
   * The asymmetry is deliberate and load-bearing. A build that cannot sell Heavy has no way for a
   * pilot to remove ads, so showing them would be indefensible — "off" here means *no ads*, never
   * "ads for everyone". `SubscriptionManager.showsAds()` enforces both halves; see the design doc §6.
   */
  val isAdsSupported: Boolean,
)

/**
 * [isDeveloperBuild] is true for anything that isn't the shipping release, computed differently
 * per host: Android from the `DEVELOPER_BUILD` BuildConfig field (`app/build.gradle.kts`, true for
 * `debug` and opt-in on `release` via `-PdeveloperBuild=true`); iOS from
 * `MainEntry.doInitKoin`'s `forceDeveloperBuild` param (`false` from `iosApp.swift` — the Debug
 * configuration is covered by the debug-binary check below) OR
 * a debug binary; web from the webpack-injected `__WINGSLOG_DEBUG__` constant.
 */
expect fun createAppCapability(isDeveloperBuild: Boolean): AppCapability
