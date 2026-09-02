package dev.fanfly.wingslog.feature.thing.update.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.analytics.AnalyticsManager
import dev.fanfly.wingslog.core.template.CurrentThingTemplate
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.feature.thing.update.viewmodel.EditThingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val thingUpdateModule = module {
  viewModel {
    EditThingViewModel(
      fleetManager = get<FleetManager>(),
      sharingManager = get<SharingManager>(),
      currentThingTemplate = get<CurrentThingTemplate>(),
      get<TemplateRegistry>(),
      analytics = get<AnalyticsManager>(),
      savedStateHandle = get<SavedStateHandle>(),
    )
  }
}
