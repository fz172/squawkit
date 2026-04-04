package dev.fanfly.wingslog.feature.inspection.viewmodel

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val inspectionUiModule = module {
  viewModel<InspectionViewModel> { InspectionViewModel(get(), get()) }
}
