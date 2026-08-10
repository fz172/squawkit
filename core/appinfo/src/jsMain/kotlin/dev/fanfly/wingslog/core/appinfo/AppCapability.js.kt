package dev.fanfly.wingslog.core.appinfo

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  // Staged rollout (#134): dev + dogfood only. GA is flipping this to `true`.
  isAircraftSharingSupported = isDeveloperBuild,
  isStressTestSupported = isDeveloperBuild,
  isCameraCaptureSupported = false,
  isAnonymousLoginSupported = false,
  // Off until the Apple provider is configured in the Firebase console (Services ID + key + the
  // `.well-known` domain verification for the custom authDomain). The web client code is in place;
  // this flag is what makes the button appear, so flipping it early would ship a button that fails
  // with `auth/operation-not-allowed`.
  isAppleSignInSupported = false,
  // Staged rollout: dev + dogfood only until GA. Off = no paywall (everything unlocked).
  isSubscriptionSupported = isDeveloperBuild,
  // Hard `false` for all of v1, not `isDeveloperBuild`: AdMob publishes no browser SDK, so the web
  // host has no ad product until phase 2 puts it on Ad Manager (design §7.3, PRD D5). The `jsMain`
  // AdView actual renders nothing regardless — this flag makes that a decision, not a side effect.
  isAdsSupported = false,
)
