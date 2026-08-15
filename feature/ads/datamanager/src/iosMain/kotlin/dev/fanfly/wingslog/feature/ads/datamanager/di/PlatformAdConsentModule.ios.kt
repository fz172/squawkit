package dev.fanfly.wingslog.feature.ads.datamanager.di

import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.ads.datamanager.impl.IosAdConsentManager
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS resolves consent through the Swift bridge — see `IosAdConsentBridge`. */
actual val platformAdConsentModule: Module = module {
  single<AdConsentManager> { IosAdConsentManager() }
}
