package dev.fanfly.wingslog.core.appinfo

actual fun createAppCapability(isDeveloperBuild: Boolean) = AppCapability(
  isDeveloperOptionsSupported = isDeveloperBuild,
  // Staged rollout (#134): dev + dogfood only. GA is flipping this to `true`.
  isAircraftSharingSupported = isDeveloperBuild,
  isStressTestSupported = isDeveloperBuild,
  isCameraCaptureSupported = false,
  isAnonymousLoginSupported = false,
  // On: the Apple provider is enabled in the Firebase console, backed by a Services ID whose return
  // URL is `https://squawkit.fanfly.dev/__/auth/handler` — the custom `authDomain` this app
  // initializes with, not the default `*.firebaseapp.com` (#398). This flag is what makes the button
  // appear, so it stays in step with that console config: turn it off again if the provider is ever
  // disabled, or the button fails with `auth/operation-not-allowed`.
  isAppleSignInSupported = true,
  // Staged rollout: dev + dogfood only until GA. Off = no paywall (everything unlocked).
  isSubscriptionSupported = isDeveloperBuild,
  // Hard `false` for all of v1, not `isDeveloperBuild`: AdMob publishes no browser SDK, so the web
  // host has no ad product until phase 2 puts it on Ad Manager (design §7.3, PRD D5). The `jsMain`
  // AdView actual renders nothing regardless — this flag makes that a decision, not a side effect.
  isAdsSupported = false,
)
