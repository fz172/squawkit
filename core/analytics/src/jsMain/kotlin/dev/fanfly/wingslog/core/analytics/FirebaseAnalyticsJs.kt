@file:JsModule("firebase/analytics")
@file:JsNonModule

package dev.fanfly.wingslog.core.analytics

// Minimal external bindings for the modular Firebase JS Analytics SDK (v10). getAnalytics() with no
// argument resolves the default app that GitLive's Firebase.initialize(...) created in main.kt, the
// same singleton the auth bindings (firebase/auth) operate on. The measurement ID is fetched from
// the Firebase backend dynamic config when it isn't present in FirebaseOptions.

external fun getAnalytics(): dynamic

external fun logEvent(
  analytics: dynamic,
  eventName: String,
  eventParams: dynamic = definedExternally,
)
