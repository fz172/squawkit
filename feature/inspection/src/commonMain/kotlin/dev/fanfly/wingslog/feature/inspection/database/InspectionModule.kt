package dev.fanfly.wingslog.feature.inspection.database

import dev.fanfly.wingslog.feature.inspection.database.impl.InspectionManagerImpl
import dev.fanfly.wingslog.feature.inspection.ui.InspectionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val inspectionModule = module {
  single<InspectionManager> { InspectionManagerImpl(get(), get()) }
  viewModel { InspectionViewModel(get(), get()) }
}
