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
  /**
   * Free-tier display ads (`docs/ads/ads_design.html` §6). The staged-rollout gate: on in dev +
   * dogfood, off in the shipping release until GA, and `false` on the web host for all of v1.
   *
   * **Default-closed**, unlike [isAircraftSharingSupported]: while this is false there are no ads
   * at all, never ads shown to everyone. A pilot must always have a way to buy their way out of
   * ads (subscriptions are unconditional now — see `SubscriptionManager`), so "off" here can only
   * ever mean *no ads*. `SubscriptionManager.showsAds()` enforces this; see the design doc §6.
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
