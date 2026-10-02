package dev.fanfly.wingslog.feature.developeroptions.aiecho.di

import dev.fanfly.wingslog.core.ai.AiJobClient
import dev.fanfly.wingslog.core.appinfo.AppCapability
import dev.fanfly.wingslog.core.storage.ThingScopeResolver
import dev.fanfly.wingslog.feature.developeroptions.aiecho.AiEchoDeveloperOptionsExtra
import dev.fanfly.wingslog.feature.developeroptions.aiecho.AiEchoRoundTrip
import dev.fanfly.wingslog.feature.developeroptions.plugin.DeveloperOptionsExtra
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Contributes the AI echo section into Developer Options. Listed directly in `commonAppModules`,
 * as a single-module feature; `isAvailable` gates it on developer builds.
 */
val aiEchoModule: Module = module {
  single {
    AiEchoRoundTrip(
      client = get<AiJobClient>(),
      fleet = get<FleetManager>(),
      scopes = get<ThingScopeResolver>(),
    )
  }
  single {
    AiEchoDeveloperOptionsExtra(
      capability = get<AppCapability>(),
      roundTrip = get<AiEchoRoundTrip>(),
    )
  } bind DeveloperOptionsExtra::class
}
