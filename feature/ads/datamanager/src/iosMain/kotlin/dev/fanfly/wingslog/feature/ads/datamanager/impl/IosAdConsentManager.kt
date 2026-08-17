package dev.fanfly.wingslog.feature.ads.datamanager.impl

import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.ads.model.AdConsentState
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import kotlinx.coroutines.flow.first

/**
 * iOS [AdConsentManager] backed by [IosAdConsentBridge]. Reads the same Developer Options
 * "UMP test device hash" field [AndroidAdConsentManager] does, so a physical iOS device is
 * registered the same way an Android one is — no Swift source edit or rebuild required.
 */
internal class IosAdConsentManager(
  private val appCapability: AppCapability,
  private val developerOptionsManager: DeveloperOptionsManager,
) : AdConsentManager {
  override suspend fun ensureConsent(): AdConsentState = presentConsentForm()

  override suspend fun isConsentRequired(): Boolean =
    IosAdConsentBridge.isConsentRequired(testDeviceHashedId())

  override suspend fun presentConsentForm(): AdConsentState =
    IosAdConsentBridge.presentConsentForm(testDeviceHashedId())

  override suspend fun presentPrivacyOptions() = IosAdConsentBridge.presentPrivacyOptions()
  override suspend fun isPrivacyOptionsAvailable(): Boolean = IosAdConsentBridge.isPrivacyOptionsAvailable()

  private suspend fun testDeviceHashedId(): String? =
    if (appCapability.isDeveloperOptionsSupported) {
      developerOptionsManager.observe().first().adConsentTestDeviceHashedId
    } else {
      null
    }
}
