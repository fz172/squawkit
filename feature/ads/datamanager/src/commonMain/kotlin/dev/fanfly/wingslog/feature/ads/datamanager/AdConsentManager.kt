package dev.fanfly.wingslog.feature.ads.datamanager

import dev.fanfly.wingslog.feature.ads.model.AdConsentState

/**
 * Resolves ad consent (GDPR/UMP on Android and iOS) ahead of the first ad request. `expect`/`actual`
 * per platform; a no-op on web, which carries no ads in v1.
 *
 * **No App Tracking Transparency, by product decision.** iOS never requests ATT alongside the CMP —
 * Apple only requires it for apps that read IDFA / track across other companies' apps and sites, not
 * to serve ads at all, and stacking it after the CMP would ask an EEA/UK pilot two consent questions
 * back to back for what reads as the same thing. iOS therefore never resolves to
 * [AdConsentState.PERSONALIZED]; only Android does, via UMP consent alone.
 *
 * **Ordering is a hard requirement** (design §8): `MobileAds.initialize()` never runs before
 * [ensureConsent]/[presentConsentForm] has resolved, and neither runs before `showsAds()` is known
 * true. A Pro user's app never starts an ad SDK and never calls a CMP.
 *
 * **The CMP call itself no longer has to wait for an ad slot to render.** [isConsentRequired] and
 * [presentConsentForm] split what [ensureConsent] does into a background check and a UI step, so a
 * caller — onboarding, specifically — can resolve whether a privacy choice is needed *before* any
 * list renders, put a priming explanation in front of it, and only then show the actual system
 * dialog, rather than have the CMP interrupt a pilot mid-scroll the first time an ad slot composes.
 * [ensureConsent] (still called from `AdSlot`) remains a safety net for whatever that upfront flow
 * didn't cover — skipped entirely, a lapsed Pro→Free downgrade mid-session, etc. — and is cheap to
 * call again: both platforms' SDKs no-op once already resolved.
 */
interface AdConsentManager {

  /**
   * Resolves consent for this session, presenting the CMP form when the platform requires it. Never
   * throws — any failure to resolve consent falls back to [AdConsentState.NON_PERSONALIZED] rather
   * than blocking the ad request indefinitely. Equivalent to calling [presentConsentForm] directly;
   * kept as its own name for `AdSlot`'s callers, which don't care about the two-step split.
   */
  suspend fun ensureConsent(): AdConsentState

  /**
   * Background-only: resolves this session's consent info (a network call, no UI — the SDK
   * equivalent of Android's `requestConsentInfoUpdate`/iOS's `ConsentInformation.requestConsentInfoUpdate`)
   * and reports whether a privacy choice is still needed, without showing anything. Lets a caller
   * decide *whether* to put UI of its own (a priming explanation) in front of the real CMP dialog,
   * before calling [presentConsentForm] to actually show it.
   */
  suspend fun isConsentRequired(): Boolean

  /**
   * Shows the actual CMP dialog if [isConsentRequired] (re-resolved fresh, so calling this without
   * calling that first still works) says one is needed, and resolves to the final
   * [AdConsentState] — the second half of what [ensureConsent] does in one call.
   */
  suspend fun presentConsentForm(): AdConsentState

  /** Re-presents the CMP's privacy-options form, for Settings' "Ad privacy settings" entry. */
  suspend fun presentPrivacyOptions()

  /**
   * Whether [presentPrivacyOptions] currently has a form to show. Both UMP SDKs expose this as an
   * in-memory flag that reads `false`/unknown until [ensureConsent] has resolved at least once this
   * process — i.e. until some ad slot has actually rendered — and even then only turns `true` for a
   * region requiring a privacy choice (EEA/UK). Lets the "Ad privacy settings" Settings row hide
   * itself instead of presenting a control that silently does nothing on tap when there is nothing
   * to re-present, whether because no ad has been viewed yet this session or because this pilot was
   * never shown a privacy choice at all.
   */
  suspend fun isPrivacyOptionsAvailable(): Boolean

  /**
   * Wipes this device's cached consent state (both platforms' UMP SDKs persist it locally across
   * launches), so the next [isConsentRequired]/[ensureConsent] resolves completely fresh — as if
   * this were a first-ever launch. Developer-only: exposed for Developer Options' "Reset ad consent"
   * so onboarding's priming explainer can be re-tested without clearing the app's local data/account
   * entirely. Has no production caller.
   */
  suspend fun resetConsent()
}
