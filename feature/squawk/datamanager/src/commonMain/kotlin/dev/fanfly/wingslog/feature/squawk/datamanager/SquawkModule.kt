package dev.fanfly.wingslog.feature.squawk.datamanager

import dev.fanfly.wingslog.feature.squawk.datamanager.impl.SquawkManagerImpl
import org.koin.dsl.module

val squawkModule = module {
  single<SquawkManager> { SquawkManagerImpl(get(), get()) }
}
