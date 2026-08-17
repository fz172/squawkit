package dev.fanfly.wingslog.core.appinfo

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  // Staged rollout (#134): dev + dogfood only. GA is flipping this to `true`.
  isAircraftSharingSupported = isDeveloperBuild,
  isStressTestSupported = isDeveloperBuild,
  isCameraCaptureSupported = false,
  isAnonymousLoginSupported = false,
  // AdMob publishes no browser SDK, so the web host has no ad product of its own until phase 2
  // puts one on Ad Manager (design §7.3, PRD D5) — the `jsMain` AdView actual renders nothing
  // regardless of this flag's value.
  isAdsSupported = false,
)
