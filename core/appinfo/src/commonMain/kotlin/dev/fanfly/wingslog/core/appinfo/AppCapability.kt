package dev.fanfly.wingslog.core.appinfo

/**
 * Single point of truth for what this build/platform/install supports. Constructed once per host
 * at Koin startup via [createAppCapability] and injected wherever a feature needs to gate on it.
 */
data class AppCapability(
  val isFeatureLabSupported: Boolean,
  /**
   * Aircraft sharing (#134, design §6.1). The staged-rollout gate: on in dev and dogfood, off in
   * the shipping release, and GA is flipping this to `true`.
   *
   * A build-time gate rather than a Feature Lab flag, because Feature Lab only exists in developer
   * builds ([isFeatureLabSupported] is `isDeveloperBuild`) — a lab toggle could never be turned on
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
   * Subscriptions / SquawkIt Pro (subscription design §1). The staged-rollout gate, like
   * [isAircraftSharingSupported]: on in dev + dogfood, off in the shipping release until GA.
   *
   * A build-time gate rather than a Feature Lab flag, because the whole paywall must be able to
   * ship dark and be turned on later. Crucially, **"off" means unlocked for everyone, not
   * restricted**: while this is false there is no gating at all — every premium feature is treated
   * as available and the Subscription entry/page is hidden. GA is flipping this to `true`.
   */
  val isSubscriptionSupported: Boolean,
)

/**
 * [isDeveloperBuild] is true for anything that isn't the shipping release, computed differently
 * per host: Android from the `DEVELOPER_BUILD` BuildConfig field (`app/build.gradle.kts`, true for
 * `debug` and opt-in on `release` via `-PdeveloperBuild=true`); iOS from
 * `MainEntry.doInitKoin`'s `forceDeveloperBuild` param (set by `#if DOGFOOD` in `iosApp.swift`) OR
 * a debug binary; web from the webpack-injected `__WINGSLOG_DEBUG__` constant.
 */
expect fun createAppCapability(isDeveloperBuild: Boolean): AppCapability
