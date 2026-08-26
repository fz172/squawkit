package dev.fanfly.wingslog.feature.notifications.model

/**
 * Where a refreshed FCM/APNs token goes (design §5.5, §7.1).
 *
 * Token refresh arrives in `viewing` (the platform SDK's callback), and has to reach the
 * account-scoped registrar in `datamanager` — the wrong direction for the module rule in AGENTS.md
 * (`viewing` may depend on `:model` but never `:datamanager`). This narrow interface in `:model`
 * closes it: `:datamanager` implements it, `:viewing` calls it, neither depends on the other. Same
 * seam shape as `core:storage`'s `CloudSyncSetting`, just inverted — the interface sits in `:model`
 * here because both sides of the rule already depend on `:model`, and `:model` depends on nothing
 * feature-specific.
 */
fun interface PushTokenSink {
  suspend fun onTokenRefreshed(token: String)
}
