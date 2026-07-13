package dev.fanfly.wingslog.feature.sharing.update.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.storage.CloudSyncSetting
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.sharing.update.InviteSheetViewModel
import dev.fanfly.wingslog.feature.sharing.update.ManageAccessViewModel
import dev.fanfly.wingslog.feature.sharing.update.RedeemViewModel
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sharingUiModule: Module = module {
  viewModel {
    ManageAccessViewModel(
      sharingManager = get<SharingManager>(),
      cloudSync = get<CloudSyncSetting>(),
      savedStateHandle = get<SavedStateHandle>()
    )
  }
  viewModel {
    InviteSheetViewModel(
      sharingManager = get<SharingManager>(),
      savedStateHandle = get<SavedStateHandle>()
    )
  }
  viewModel {
    RedeemViewModel(
      sharingManager = get<SharingManager>(),
      auth = get<FirebaseAuth>()
    )
  }
}
