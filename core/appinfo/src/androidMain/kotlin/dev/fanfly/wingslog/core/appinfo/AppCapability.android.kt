package dev.fanfly.wingslog.core.appinfo

/** `market://` opens the Play app directly; Play resolves the https form if it is not installed. */
internal const val PLAY_LISTING = "market://details?id=dev.fanfly.wingslog"

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  isCameraCaptureSupported = true,
  isAnonymousLoginSupported = true,
  // GA (#386, P9): was dev + dogfood only until ads GA. Off = NO ads.
  isAdsSupported = true,
  // Data logs: developer builds only until V1 is complete, then true everywhere.
  isDataLogsSupported = isDeveloperBuild,
  supportUrl = "https://squawkit.fanfly.dev/support.html",
  termsUrl = "https://squawkit.fanfly.dev/privacy.html",
  storeListingUrl = PLAY_LISTING,
)
