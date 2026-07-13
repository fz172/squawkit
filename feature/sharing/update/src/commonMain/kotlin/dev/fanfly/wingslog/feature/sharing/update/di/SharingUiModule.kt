package dev.fanfly.wingslog.feature.sharing.update.di

import dev.fanfly.wingslog.feature.sharing.update.InviteSheetViewModel
import dev.fanfly.wingslog.feature.sharing.update.ManageAccessViewModel
import dev.fanfly.wingslog.feature.sharing.update.RedeemViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import androidx.lifecycle.SavedStateHandle
import dev.gitlive.firebase.auth.FirebaseAuth

val sharingUiModule: Module = module {
  viewModel { ManageAccessViewModel(sharingManager = get<SharingManager>(), savedStateHandle = get<SavedStateHandle>()) }
  viewModel { InviteSheetViewModel(sharingManager = get<SharingManager>(), savedStateHandle = get<SavedStateHandle>()) }
  viewModel { RedeemViewModel(sharingManager = get<SharingManager>(), auth = get<FirebaseAuth>()) }
}
