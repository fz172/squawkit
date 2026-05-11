package dev.fanfly.wingslog.feature.squawk.update.viewmodel

import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.dsl.module

val squawkUiModule = module {
  viewModel { SquawkFormViewModel(get(), get()) }
}
