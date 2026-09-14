package dev.fanfly.wingslog.feature.datalog.viewing.di

import dev.fanfly.wingslog.core.auth.AuthManager
import dev.fanfly.wingslog.feature.datalog.datamanager.DataLogManager
import dev.fanfly.wingslog.feature.datalog.viewing.list.DataLogListViewModel
import dev.fanfly.wingslog.id.ThingId
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dataLogViewingModule: Module = module {
  viewModel { params ->
    DataLogListViewModel(
      get<DataLogManager>(),
      get<AuthManager>(),
      ThingId(params.get<String>(0))
    )
  }
}
