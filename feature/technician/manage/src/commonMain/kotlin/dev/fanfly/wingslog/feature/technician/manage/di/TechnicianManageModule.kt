package dev.fanfly.wingslog.feature.technician.manage.di

import dev.fanfly.wingslog.feature.technician.manage.viewmodel.EditTechnicianViewModel
import dev.fanfly.wingslog.feature.technician.manage.viewmodel.TechnicianListViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val technicianManageModule = module {
  viewModelOf(::TechnicianListViewModel)

  factory { (id: String?) ->
    EditTechnicianViewModel(get(), id)
  }
}
