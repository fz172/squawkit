package dev.fanfly.wingslog.feature.dashboard.host.di

import androidx.lifecycle.SavedStateHandle
import dev.fanfly.wingslog.core.nav.Screen
import dev.fanfly.wingslog.core.template.TemplateRegistry
import dev.fanfly.wingslog.feature.dashboard.host.ThingOverviewViewModel
import dev.fanfly.wingslog.feature.fleet.datamanager.FleetManager
import dev.fanfly.wingslog.feature.sharing.datamanager.SharingManager
import dev.fanfly.wingslog.id.ThingId
import dev.gitlive.firebase.auth.FirebaseAuth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The dashboard host: the section ViewModel for one thing. */
val dashboardHostModule = module {
  // thingId comes from an explicit parameter (adaptive shell, ambient selection) when present,
  // otherwise from the navigation SavedStateHandle (legacy maintenance_overview/{thingId} route).
  viewModel { params ->
    val thingId = params.getOrNull<ThingId>()
      ?: params.getOrNull<String>()?.let(::ThingId)
      ?: checkNotNull(get<SavedStateHandle>().get<String>(Screen.THING_ID)).let(::ThingId)
    ThingOverviewViewModel(
      get<FleetManager>(),
      get<SharingManager>(),
      get<TemplateRegistry>(),
      get<FirebaseAuth>(),
      thingId,
    )
  }
}
