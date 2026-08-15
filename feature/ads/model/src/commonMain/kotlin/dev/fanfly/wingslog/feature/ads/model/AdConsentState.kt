package dev.fanfly.wingslog.feature.ads.model

/**
 * What [dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager.ensureConsent] resolved to,
 * before any ad request is made (design §8).
 */
enum class AdConsentState {
  /** Consent obtained (or not required in this region): the request may target the user. */
  PERSONALIZED,

  /**
   * No personalized targeting — either the CMP requires consent and the user declined, or consent
   * could not be resolved. A request is still made; it just carries no targeting.
   */
  NON_PERSONALIZED,

  /** The user declined the CMP outright. Treated identically to [NON_PERSONALIZED] at request time. */
  DENIED,
}
