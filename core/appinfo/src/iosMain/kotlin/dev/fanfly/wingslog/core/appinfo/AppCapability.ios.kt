package dev.fanfly.wingslog.core.appinfo

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  // Staged rollout (#134): dev + dogfood only. GA is flipping this to `true`.
  isAircraftSharingSupported = isDeveloperBuild,
  isStressTestSupported = isDeveloperBuild,
  isCameraCaptureSupported = true,
  isAnonymousLoginSupported = true,
  // GA'd ahead of Android/web (#443 still open): iOS has no dogfood build state (Debug/Release
  // only, forceDeveloperBuild is hardcoded false in iosApp.swift), so a Release archive always
  // resolves isDeveloperBuild=false — there was no way to see the paywall in a TestFlight build
  // without this being unconditionally true. RevenueCat still picks the right key independently
  // (RevenueCatApiKey.resolve keys off isDeveloperBuild, untouched by this).
  isSubscriptionSupported = true,
  // Staged rollout: dev + dogfood only until GA. Off = NO ads (the inverse of the gates above).
  // GA here also waits on the P8 Swift-bridge validation, since iOS has no CI build.
  isAdsSupported = isDeveloperBuild,
)
