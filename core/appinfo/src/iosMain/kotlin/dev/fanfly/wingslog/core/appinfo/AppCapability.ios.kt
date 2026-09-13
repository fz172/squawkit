package dev.fanfly.wingslog.core.appinfo

/**
 * The listing, by numeric app id so a slug change cannot break it.
 *
 * `https` rather than the `itms-apps://` analogue of Android’s `market://`: iOS hands
 * apps.apple.com links to the App Store app anyway, and `itms-apps://` is a silent no-op
 * wherever nothing claims that scheme — the Simulator ships no App Store app.
 */
internal const val APP_STORE_LISTING = "https://apps.apple.com/app/id6801955033"

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  isCameraCaptureSupported = true,
  isAnonymousLoginSupported = true,
  // GA (#386, P9): was dev + dogfood only until ads GA, gated on the P8 Swift-bridge device
  // validation (done — see #385) since iOS has no CI build. Off = NO ads.
  isAdsSupported = true,
  supportUrl = "https://squawkit.fanfly.dev/support.html",
  termsUrl = "https://squawkit.fanfly.dev/privacy.html",
)
