package dev.fanfly.wingslog.core.appinfo

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  isStressTestSupported = isDeveloperBuild,
  isCameraCaptureSupported = true,
  isAnonymousLoginSupported = true,
  // GA (#386, P9): was dev + dogfood only until ads GA. Off = NO ads.
  isAdsSupported = true,
  // Dev-only while the search and filter phases land (project #11).
  isSearchFilterSupported = isDeveloperBuild,
)
