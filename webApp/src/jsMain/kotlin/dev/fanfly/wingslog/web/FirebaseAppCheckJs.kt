@file:JsModule("firebase/app-check")
@file:JsNonModule

package dev.fanfly.wingslog.web

// Direct binding to the modular Firebase JS App Check SDK. The web app must attest with App Check
// or every `enforceAppCheck` callable (redeem/revoke/updateRole/export) is rejected as
// `unauthenticated`. Android does this in WingsLogApplication; this is the web equivalent.

external fun initializeAppCheck(app: dynamic, options: dynamic): dynamic

external class ReCaptchaV3Provider(siteKey: String)

// Fetches a current App Check token from an initialized AppCheck instance. Used by the attachment
// broker's streamBlob download, which requires an X-Firebase-AppCheck header (P8.4 #245).
external fun getToken(appCheck: dynamic, forceRefresh: Boolean): kotlin.js.Promise<AppCheckTokenResult>

external interface AppCheckTokenResult {
  val token: String
}
