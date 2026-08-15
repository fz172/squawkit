package dev.fanfly.wingslog.feature.ads.datamanager.impl

import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.ads.model.AdConsentState

/** iOS [AdConsentManager] backed by [IosAdConsentBridge]. */
internal class IosAdConsentManager : AdConsentManager {
  override suspend fun ensureConsent(): AdConsentState = IosAdConsentBridge.ensureConsent()
  override suspend fun presentPrivacyOptions() = IosAdConsentBridge.presentPrivacyOptions()
}
