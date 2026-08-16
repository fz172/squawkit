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
 * **Ordering is a hard requirement** (design §8): the gate chain is `showsAds()` → `ensureConsent()`
 * → `MobileAds.initialize()` → first request, and every step is lazy. A Pro user's app never
 * starts an ad SDK and never calls a CMP — nothing here runs until a caller has already decided an
 * ad slot will render.
 */
interface AdConsentManager {

  /**
   * Resolves consent for this session, presenting the CMP form when the platform requires it. Never
   * throws — any failure to resolve consent falls back to [AdConsentState.NON_PERSONALIZED] rather
   * than blocking the ad request indefinitely.
   */
  suspend fun ensureConsent(): AdConsentState

  /** Re-presents the CMP's privacy-options form, for Settings' "Ad privacy settings" entry. */
  suspend fun presentPrivacyOptions()
}
