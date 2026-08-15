package dev.fanfly.wingslog.feature.ads.datamanager.di

import android.app.Application
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.lifecycle.CurrentActivityProvider
import dev.fanfly.wingslog.feature.ads.datamanager.AdConsentManager
import dev.fanfly.wingslog.feature.ads.datamanager.impl.AndroidAdConsentManager
import dev.fanfly.wingslog.feature.developeroptions.datamanager.DeveloperOptionsManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android resolves consent through Google UMP, against the foreground activity. */
actual val platformAdConsentModule: Module = module {
  single<AdConsentManager> {
    AndroidAdConsentManager(
      application = androidContext() as Application,
      activityProvider = get<CurrentActivityProvider>(),
      appCapability = get<AppCapability>(),
      developerOptionsManager = get<DeveloperOptionsManager>(),
    )
  }
}
