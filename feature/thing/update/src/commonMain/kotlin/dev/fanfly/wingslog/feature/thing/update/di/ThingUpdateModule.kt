package dev.fanfly.wingslog.feature.thing.update.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.thing.update.viewmodel.EditThingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val aircraftUpdateModule = module {
  viewModel {
    EditThingViewModel(
      get<FleetManager>(),
      get<SharingManager>(),
      get<CurrentThingTemplate>(),
      get<SavedStateHandle>()
    )
  }
}
