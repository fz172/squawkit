package dev.fanfly.wingslog.di

import dev.fanfly.wingslog.core.appinfo.createAppCapability
import dev.fanfly.wingslog.core.di.commonAppModules
import dev.fanfly.wingslog.feature.stresstest.config.stressTestKoinModules
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

fun initKoin(
  isDeveloperBuild: Boolean = false,
  appDeclaration: KoinAppDeclaration = {},
) = startKoin {
  appDeclaration()
  val allModules = commonAppModules + stressTestKoinModules() + listOf(
    module { single { createAppCapability(isDeveloperBuild) } },
  )
  modules(allModules)
}
