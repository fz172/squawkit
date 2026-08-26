package dev.fanfly.wingslog.core.appinfo

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  isStressTestSupported = isDeveloperBuild,
  isCameraCaptureSupported = true,
  isAnonymousLoginSupported = true,
  // GA (#386, P9): was dev + dogfood only until ads GA, gated on the P8 Swift-bridge device
  // validation (done — see #385) since iOS has no CI build. Off = NO ads.
  isAdsSupported = true,
)
