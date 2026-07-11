@file:JsModule("firebase/app-check")
@file:JsNonModule

package dev.fanfly.wingslog.web

// Direct binding to the modular Firebase JS App Check SDK. The web app must attest with App Check
// or every `enforceAppCheck` callable (redeem/revoke/updateRole/export) is rejected as
// `unauthenticated`. Android does this in WingsLogApplication; this is the web equivalent.

external fun initializeAppCheck(app: dynamic, options: dynamic): dynamic

external class ReCaptchaV3Provider(siteKey: String)
