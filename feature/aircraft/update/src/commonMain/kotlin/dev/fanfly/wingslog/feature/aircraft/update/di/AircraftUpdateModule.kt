package dev.fanfly.wingslog.feature.aircraft.update.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.feature.aircraft.update.viewmodel.EditAircraftViewModel
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val aircraftUpdateModule = module {
  viewModel {
    EditAircraftViewModel(
      get<FleetManager>(),
      get<SavedStateHandle>()
    )
  }
}
