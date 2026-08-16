package dev.fanfly.wingslog.core.appinfo

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  // Staged rollout (#134): dev + dogfood only. GA is flipping this to `true`.
  isAircraftSharingSupported = isDeveloperBuild,
  isStressTestSupported = isDeveloperBuild,
  isCameraCaptureSupported = true,
  isAnonymousLoginSupported = true,
  // Staged rollout: dev + dogfood only until ads GA, which also waits on the P8 Swift-bridge
  // validation, since iOS has no CI build. Off = NO ads.
  isAdsSupported = isDeveloperBuild,
)
