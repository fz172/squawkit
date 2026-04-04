package dev.fanfly.wingslog.feature.inspection.datamanager

import dev.fanfly.wingslog.feature.inspection.datamanager.impl.InspectionManagerImpl
import org.koin.dsl.module

val inspectionModule = module {
  single<InspectionManager> { InspectionManagerImpl(get(), get()) }
}
