package dev.fanfly.wingslog.feature.aircraft.update.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.feature.aircraft.update.viewmodel.EditThingViewModel
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val aircraftUpdateModule = module {
  viewModel {
    EditThingViewModel(
      get<FleetManager>(),
      get<SharingManager>(),
      get<SavedStateHandle>()
    )
  }
}
