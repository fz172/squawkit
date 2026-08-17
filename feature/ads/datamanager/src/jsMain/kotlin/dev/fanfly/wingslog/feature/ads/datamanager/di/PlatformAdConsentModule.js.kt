package dev.fanfly.wingslog.feature.ads.datamanager.di

import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.ads.model.AdConsentState
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * No ad product on web in v1 (design §7.3), so nothing ever calls [AdConsentManager] here — this
 * exists only so the shared `adsModule` wiring compiles for Kotlin/JS, the same reason `AdView.js.kt`
 * renders nothing rather than not existing at all.
 */
private object NoOpAdConsentManager : AdConsentManager {
  override suspend fun ensureConsent(): AdConsentState = AdConsentState.NON_PERSONALIZED
  override suspend fun presentPrivacyOptions() = Unit
  override suspend fun isPrivacyOptionsAvailable(): Boolean = false
}

actual val platformAdConsentModule: Module = module {
  single<AdConsentManager> { NoOpAdConsentManager }
}
