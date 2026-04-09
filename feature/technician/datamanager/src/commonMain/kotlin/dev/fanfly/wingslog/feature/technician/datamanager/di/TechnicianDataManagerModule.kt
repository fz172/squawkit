package dev.fanfly.wingslog.feature.technician.datamanager.di

import dev.fanfly.wingslog.feature.technician.datamanager.TechnicianManager
import dev.fanfly.wingslog.feature.technician.datamanager.impl.TechnicianManagerImpl
import org.koin.dsl.module

val technicianDataManagerModule = module {
  single<TechnicianManager> { TechnicianManagerImpl(get(), get()) }
}
