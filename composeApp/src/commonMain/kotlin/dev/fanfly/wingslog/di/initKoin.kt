package dev.fanfly.wingslog.di

import dev.fanfly.wingslog.DogfoodFeatureExtensions
import dev.fanfly.wingslog.NoOpDogfoodExtensions
import dev.fanfly.wingslog.core.appinfo.BuildInfo
import dev.fanfly.wingslog.core.di.commonAppModules
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(
  dogfoodExtensions: DogfoodFeatureExtensions = NoOpDogfoodExtensions,
  isDeveloperBuild: Boolean = false,
  appDeclaration: KoinAppDeclaration = {},
) = startKoin {
  appDeclaration()
  val allModules = dogfoodExtensions.koinModules() + commonAppModules + listOf(
    module { single<DogfoodFeatureExtensions> { dogfoodExtensions } },
    module { single { BuildInfo(isDeveloperBuild = isDeveloperBuild) } },
  )
  modules(allModules)
}
