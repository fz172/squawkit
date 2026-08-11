package dev.fanfly.wingslog.core.appinfo

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  // Staged rollout (#134): dev + dogfood only. GA is flipping this to `true`.
  isAircraftSharingSupported = isDeveloperBuild,
  isStressTestSupported = isDeveloperBuild,
  isCameraCaptureSupported = true,
  isAnonymousLoginSupported = true,
  isAppleSignInSupported = true,
  // Off until NativeGoogleSignInProvider returns credential material instead of signing in
  // Swift-side — until then the upgrade would fail, so the picker must not offer it.
  isGoogleUpgradeSupported = false,
  // Staged rollout: dev + dogfood only until GA. Off = no paywall (everything unlocked).
  isSubscriptionSupported = isDeveloperBuild,
  // Staged rollout: dev + dogfood only until GA. Off = NO ads (the inverse of the gates above).
  // GA here also waits on the P8 Swift-bridge validation, since iOS has no CI build.
  isAdsSupported = isDeveloperBuild,
)
