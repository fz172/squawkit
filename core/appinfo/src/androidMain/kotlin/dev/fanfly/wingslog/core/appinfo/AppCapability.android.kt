package dev.fanfly.wingslog.core.appinfo

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  // Staged rollout (#134): dev + dogfood only. GA is flipping this to `true`.
  isAircraftSharingSupported = isDeveloperBuild,
  isStressTestSupported = isDeveloperBuild,
  isCameraCaptureSupported = true,
  isAnonymousLoginSupported = true,
  isAppleSignInSupported = false,
  // Google is the platform provider here, and Credential Manager hands back a credential to link.
  isGoogleUpgradeSupported = true,
  // Staged rollout: dev + dogfood only until GA. Off = no paywall (everything unlocked).
  isSubscriptionSupported = isDeveloperBuild,
  // Staged rollout: dev + dogfood only until GA. Off = NO ads (the inverse of the gates above).
  isAdsSupported = isDeveloperBuild,
)
