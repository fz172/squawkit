package dev.fanfly.wingslog.core.appinfo

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  isStressTestSupported = isDeveloperBuild,
  isNotificationsSupported = isDeveloperBuild,
  isCameraCaptureSupported = true,
  isAnonymousLoginSupported = true,
  // GA (#386, P9): was dev + dogfood only until ads GA. Off = NO ads.
  isAdsSupported = true,
)
