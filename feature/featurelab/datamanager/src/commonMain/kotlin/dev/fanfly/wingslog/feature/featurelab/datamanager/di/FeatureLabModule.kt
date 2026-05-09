package dev.fanfly.wingslog.feature.featurelab.datamanager.di

import dev.fanfly.wingslog.feature.featurelab.datamanager.FeatureLabManager
import dev.fanfly.wingslog.feature.featurelab.datamanager.impl.FeatureLabManagerImpl
import org.koin.dsl.module

val featureLabModule = module {
  single<FeatureLabManager> {
    FeatureLabManagerImpl(
      firebaseAuth = get(),
      storeFactory = get(),
    )
  }
}
