package dev.fanfly.wingslog.feature.aircraft.inspection.database

import dev.fanfly.wingslog.feature.aircraft.inspection.database.impl.InspectionManagerImpl
import org.koin.dsl.module

val inspectionModule = module {
  single<InspectionManager> { InspectionManagerImpl(get(), get()) }
}
