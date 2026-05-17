package dev.fanfly.wingslog.feature.stresstest.di

import dev.fanfly.wingslog.feature.stresstest.StressTestViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val stressTestModule = module {
  viewModel { StressTestViewModel(get(), get(), get(), get(), get()) }
}
