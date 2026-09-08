package dev.fanfly.wingslog.feature.ads.di

import dev.fanfly.wingslog.feature.ads.datamanager.di.adsDataManagerModule
import dev.fanfly.wingslog.feature.ads.datamanager.di.platformAdConsentModule
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Bundles every Koin module of the ads feature (free-tier display ads) into the one entry
 * `commonAppModules` lists, so `core/di` depends on this module rather than on each submodule.
 */
val adsModule: Module = module {
  includes(
    adsDataManagerModule,
    // Google UMP on Android/iOS's Swift bridge; the no-op binding on web. Same shape as
    // platformBillingModule next to subscriptionDataManagerModule.
    platformAdConsentModule,
  )
}
