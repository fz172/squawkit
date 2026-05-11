package dev.fanfly.wingslog.feature.squawk.update.viewmodel

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.feature.squawk.datamanager.SquawkManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val squawkUiModule = module {
  viewModel { SquawkFormViewModel(get<SquawkManager>(), get<SavedStateHandle>()) }
}
