package dev.fanfly.wingslog.feature.ads.datamanager

import dev.fanfly.wingslog.feature.ads.model.AdConsentState

/**
 * Resolves ad consent (GDPR/UMP on Android and iOS, App Tracking Transparency on iOS) ahead of the
 * first ad request. `expect`/`actual` per platform; a no-op on web, which carries no ads in v1.
 *
 * **Ordering is a hard requirement** (design §8): the gate chain is `showsAds()` → `ensureConsent()`
 * → `MobileAds.initialize()` → first request, and every step is lazy. A Heavy user's app never
 * starts an ad SDK, never calls a CMP, and never sees an ATT prompt — nothing here runs until a
 * caller has already decided an ad slot will render.
 */
interface AdConsentManager {

  /**
   * Resolves consent for this session, presenting the CMP form (and, on iOS, the ATT prompt at
   * first ad-eligible list render) when the platform requires it. Never throws — any failure to
   * resolve consent falls back to [AdConsentState.NON_PERSONALIZED] rather than blocking the ad
   * request indefinitely.
   */
  suspend fun ensureConsent(): AdConsentState

  /** Re-presents the CMP's privacy-options form, for Settings' "Ad privacy settings" entry. */
  suspend fun presentPrivacyOptions()
}
