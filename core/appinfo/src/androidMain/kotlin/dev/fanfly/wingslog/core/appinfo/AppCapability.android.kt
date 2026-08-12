package dev.fanfly.wingslog.core.appinfo

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  // Staged rollout (#134): dev + dogfood only. GA is flipping this to `true`.
  isAircraftSharingSupported = isDeveloperBuild,
  isStressTestSupported = isDeveloperBuild,
  isCameraCaptureSupported = true,
  isAnonymousLoginSupported = true,
  // #408. No native Apple SDK on Android — Firebase runs it as a generic OAuth flow in a Custom
  // Tab, which is why this needs a foreground Activity (see CurrentActivityProvider).
  isAppleSignInSupported = true,
  // Staged rollout: dev + dogfood only until GA. Off = no paywall (everything unlocked).
  isSubscriptionSupported = isDeveloperBuild,
  // Staged rollout: dev + dogfood only until GA. Off = NO ads (the inverse of the gates above).
  isAdsSupported = isDeveloperBuild,
)
