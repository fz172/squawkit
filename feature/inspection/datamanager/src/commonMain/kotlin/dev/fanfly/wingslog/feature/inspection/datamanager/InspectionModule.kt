package dev.fanfly.wingslog.feature.inspection.datamanager

import dev.fanfly.wingslog.feature.inspection.datamanager.impl.InspectionDueManagerImpl
import dev.fanfly.wingslog.feature.inspection.datamanager.impl.InspectionDataManagerImpl
import org.koin.dsl.module

val inspectionModule = module {
  single<InspectionDataManager> { InspectionDataManagerImpl(get(), get()) }
  single<InspectionDueManager> { InspectionDueManagerImpl() }
}
