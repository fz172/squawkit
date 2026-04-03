package dev.fanfly.wingslog.di

import dev.fanfly.wingslog.core.auth.di.authModule
import dev.fanfly.wingslog.core.database.di.commonFirebaseModule
import dev.fanfly.wingslog.feature.aircraft.database.impl.aircraftDatabaseModule
import dev.fanfly.wingslog.feature.aircraft.inspection.database.inspectionModule
import dev.fanfly.wingslog.feature.aircraft.di.aircraftModule
import dev.fanfly.wingslog.feature.fleet.database.impl.fleetDatabaseModule
import dev.fanfly.wingslog.feature.fleet.di.fleetModule
import dev.fanfly.wingslog.feature.settings.di.settingsModule
import dev.fanfly.wingslog.feature.userprofile.database.impl.userProfileDatabaseModule
import dev.fanfly.wingslog.feature.userprofile.di.userProfileModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(appDeclaration: KoinAppDeclaration = {}) =
  startKoin {
    appDeclaration()
    modules(
      commonFirebaseModule,
      authModule,
      userProfileDatabaseModule,
      aircraftDatabaseModule,
      inspectionModule,
      fleetDatabaseModule,
      appModule,
      userProfileModule,
      settingsModule,
      fleetModule,
      aircraftModule,
    )
  }
