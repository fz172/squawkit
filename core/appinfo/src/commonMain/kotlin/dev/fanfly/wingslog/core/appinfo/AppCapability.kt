package dev.fanfly.wingslog.core.appinfo

/**
 * Single point of truth for what this build/platform/install supports. Constructed once per host
 * at Koin startup via [createAppCapability] and injected wherever a feature needs to gate on it.
 */
data class AppCapability(
  val isDeveloperOptionsSupported: Boolean,
  val isStressTestSupported: Boolean,
  /**
   * Staged rollout, not a platform-capability statement — every platform can show notifications
   * (see `docs/notifications/notifications_design.md`). The feature ships incrementally across many
   * PRs, and Settings' entry point still points at a "coming soon" placeholder until P1.9+ lands, so
   * this keeps it out of real users' hands the same way [isStressTestSupported] does for the
   * fake-data generator. Remove once the feature is actually finished — do not repurpose this as a
   * real per-platform notification-support flag; `feature:notifications:permission`'s
   * `PermissionState.UNSUPPORTED` already answers that question at runtime, per-device, which a
   * build-time flag cannot.
   */
  val isNotificationsSupported: Boolean,
  val isCameraCaptureSupported: Boolean,
  val isAnonymousLoginSupported: Boolean,
  /**
   * Free-tier display ads (`docs/ads/ads_design.html` §6, GA'd #386/P9) on Android and iOS. Web has
   * no ad product of its own regardless of this flag's value — no AdMob web SDK; phase 2 lands on
   * Ad Manager (design §7.3, PRD D5) — so `jsMain`'s `AdView` actual renders nothing either way.
   *
   * **Default-closed**: while this is false there are no ads at all, never ads shown to everyone.
   * A pilot must always have a way to buy their way out of ads (subscriptions are unconditional
   * now — see `SubscriptionManager`), so "off" here can only ever mean *no ads*.
   * `SubscriptionManager.showsAds()` enforces this; see the design doc §6.
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
