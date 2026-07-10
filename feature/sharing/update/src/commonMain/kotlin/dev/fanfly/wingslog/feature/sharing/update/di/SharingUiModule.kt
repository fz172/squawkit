package dev.fanfly.wingslog.feature.sharing.update.di

import dev.fanfly.wingslog.feature.sharing.update.InviteSheetViewModel
import dev.fanfly.wingslog.feature.sharing.update.ManageAccessViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharingUiModule: Module = module {
  viewModel { ManageAccessViewModel(sharingManager = get(), savedStateHandle = get()) }
  viewModel { InviteSheetViewModel(sharingManager = get(), savedStateHandle = get()) }
}
