package dev.fanfly.wingslog.feature.sharing.datamanager

import dev.fanfly.wingslog.feature.sharing.datamanager.impl.SharingManagerImpl
import org.koin.dsl.module

val sharingModule = module {
  single<SharingManager> { SharingManagerImpl() }
}
